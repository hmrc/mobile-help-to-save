/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mobilehelptosave.services

import java.time.LocalDate

import cats.MonadError
import cats.data.EitherT
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.{AccountServiceConfig, MilestonesConfig}
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain.{MilestoneCheckResult, _}
import uk.gov.hmrc.mobilehelptosave.repository._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait AccountService[F[_]] {
  type Result[T] = Either[ErrorInfo, T]

  def account(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[Account]]]

  def setSavingsGoal(nino:    Nino, savingsGoal: SavingsGoal)(implicit hc: HeaderCarrier): F[Result[Unit]]
  def getSavingsGoal(nino:    Nino)(implicit hc: HeaderCarrier): F[Result[Option[SavingsGoal]]]
  def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Unit]]

  def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier): F[Result[List[SavingsGoalEvent]]]
}

class HtsAccountService[F[_]](
  logger:                       LoggerLike,
  config:                       AccountServiceConfig,
  helpToSaveEnrolmentStatus:    HelpToSaveEnrolmentStatus[F],
  helpToSaveGetAccount:         HelpToSaveGetAccount[F],
  savingsGoalEventRepo:         SavingsGoalEventRepo[F],
  balanceMilestonesService:     BalanceMilestonesService[F],
  bonusPeriodMilestonesService: BonusPeriodMilestonesService[F],
  milestonesConfig:             MilestonesConfig
)(implicit F:                   MonadError[F, Throwable])
    extends AccountService[F] {

  override def setSavingsGoal(nino: Nino, savingsGoal: SavingsGoal)(implicit hc: HeaderCarrier): F[Result[Unit]] =
    withValidSavingsAmount(savingsGoal.goalAmount) {
      withHelpToSaveAccount(nino) { acc =>
        withEnoughSavingsHeadroom(savingsGoal.goalAmount, acc) {
          trappingRepoExceptions("error writing savings goal to repo", savingsGoalEventRepo.setGoal(nino, savingsGoal.goalAmount))
        }
      }
    }

  override def getSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[SavingsGoal]]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error reading goal from events repo", savingsGoalEventRepo.getGoal(nino))
    }

  override def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Unit]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error writing to savings goal events repo", savingsGoalEventRepo.deleteGoal(nino))
    }

  override def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier): F[Result[List[SavingsGoalEvent]]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error reading from savings goal events repo", savingsGoalEventRepo.getEvents(nino))
    }

  override def account(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[Account]]] =
    EitherT(helpToSaveEnrolmentStatus.enrolmentStatus()).flatMap {
      case true =>
        EitherT(fetchAccountWithGoal(nino)).flatMap {
          case Some(account) =>
            EitherT.liftF[F, ErrorInfo, Option[Account]](for {
              _ <- if (milestonesConfig.balanceMilestoneCheckEnabled) balanceMilestonesService.balanceMilestoneCheck(nino, account.balance)
                  else F.pure(())
              _ <- if (milestonesConfig.bonusPeriodMilestoneCheckEnabled)
                    bonusPeriodMilestonesService.bonusPeriodMilestoneCheck(nino, account.bonusTerms, account.balance)
                  else F.pure(())
            } yield Some(account))
          case _ => EitherT.rightT[F, ErrorInfo](Option.empty[Account])
        }
      case false =>
        EitherT.rightT[F, ErrorInfo](Option.empty[Account])
    }.value

  protected def withValidSavingsAmount[T](goal: Double)(fn: => F[Result[T]])(implicit hc: HeaderCarrier): F[Result[T]] =
    if (goal < 1.0 || BigDecimal(goal).scale > 2)
      F.pure(ErrorInfo.ValidationError(s"goal amount should be a valid monetary amount [$goal]").asLeft)
    else
      fn

  protected def withEnoughSavingsHeadroom[T](goal: Double, acc: HelpToSaveAccount)(fn: => F[Result[T]])(implicit hc: HeaderCarrier): F[Result[T]] = {
    val maxGoal = acc.maximumPaidInThisMonth
    if (goal > maxGoal)
      F.pure(ErrorInfo.ValidationError(s"goal amount should be in range 1 to $maxGoal").asLeft)
    else
      fn
  }

  /**
    * Check if the nino has an NS&I help-to-save account associated with it. If so, run the supplied function on it,
    * otherwise map to an appropriate ErrorInfo value.
    */
  protected def withHelpToSaveAccount[T](nino: Nino)(f: HelpToSaveAccount => F[Result[T]])(implicit hc: HeaderCarrier): F[Result[T]] =
    helpToSaveGetAccount.getAccount(nino).flatMap {
      case Right(Some(account)) => f(account)
      case Right(None)          => F.pure(ErrorInfo.AccountNotFound.asLeft)
      case Left(errorInfo)      => F.pure(errorInfo.asLeft)
    }

  protected def trappingRepoExceptions[T](msg: String, f: => F[T]): F[Result[T]] =
    f.map(_.asRight[ErrorInfo]).recover {
      case NonFatal(t) =>
        logger.error(msg, t)
        ErrorInfo.General.asLeft[T]
    }

  protected def fetchSavingsGoal(nino: Nino): F[Result[Option[SavingsGoal]]] =
    savingsGoalEventRepo.getGoal(nino).map(_.asRight[ErrorInfo]).recover {
      case t =>
        logger.warn("call to repo to retrieve savings goal failed", t)
        ErrorInfo.General.asLeft[Option[SavingsGoal]]
    }

  protected def fetchAccountWithGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[Account]]] =
    (
      helpToSaveGetAccount.getAccount(nino),
      fetchSavingsGoal(nino)
    ).mapN {
      case (Right(Some(account)), Right(goal)) =>
        Some(
          Account(
            account,
            inAppPaymentsEnabled = config.inAppPaymentsEnabled,
            savingsGoalsEnabled  = config.savingsGoalsEnabled,
            logger,
            LocalDate.now(),
            goal)).asRight

      case (Left(errorInfo), _) => errorInfo.asLeft
      case (_, Left(errorInfo)) => errorInfo.asLeft

      case (Right(None), _) =>
        logger.warn(s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")
        None.asRight
    }

}
