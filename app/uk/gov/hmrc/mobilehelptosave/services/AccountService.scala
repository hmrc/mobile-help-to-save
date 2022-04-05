/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.{LocalDate, YearMonth}
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
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount, HelpToSaveGetTransactions}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._

import java.time.temporal.TemporalAdjusters
import scala.util.control.NonFatal

trait AccountService[F[_]] {
  type Result[T] = Either[ErrorInfo, T]

  def account(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[Account]]]

  def setSavingsGoal(
    nino:        Nino,
    savingsGoal: SavingsGoal
  )(implicit hc: HeaderCarrier
  ): F[Result[Unit]]
  def getSavingsGoal(nino:    Nino)(implicit hc: HeaderCarrier): F[Result[Option[SavingsGoal]]]
  def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Unit]]

  def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier): F[Result[List[SavingsGoalEvent]]]
}

class HtsAccountService[F[_]](
  logger:                        LoggerLike,
  config:                        AccountServiceConfig,
  helpToSaveEnrolmentStatus:     HelpToSaveEnrolmentStatus[F],
  helpToSaveGetAccount:          HelpToSaveGetAccount[F],
  savingsGoalEventRepo:          SavingsGoalEventRepo[F],
  balanceMilestonesService:      BalanceMilestonesService[F],
  bonusPeriodMilestonesService:  BonusPeriodMilestonesService[F],
  bonusReachedMilestonesService: BonusReachedMilestonesService[F],
  mongoUpdateService:            MongoUpdateService[F],
  savingsUpdateService:          SavingsUpdateService,
  helpToSaveGetTransactions:     HelpToSaveGetTransactions[F],
  milestonesConfig:              MilestonesConfig
)(implicit F:                    MonadError[F, Throwable])
    extends AccountService[F] {

  override def setSavingsGoal(
    nino:        Nino,
    savingsGoal: SavingsGoal
  )(implicit hc: HeaderCarrier
  ): F[Result[Unit]] =
    withValidSavingsAmount(savingsGoal.goalAmount) {
      withHelpToSaveAccount(nino) { acc =>
        withEnoughSavingsHeadroom(savingsGoal.goalAmount, acc) {
          trappingRepoExceptions(
            "error writing savings goal to repo",
            savingsGoalEventRepo.setGoal(nino,
                                         savingsGoal.goalAmount,
                                         savingsGoal.goalName,
                                         acc.bonusTerms(1).bonusPaidOnOrAfterDate)
          )
        }
      }
    }

  protected def withValidSavingsAmount[T](goal: Option[Double])(fn: => F[Result[T]]): F[Result[T]] =
    goal match {
      case Some(goal) if goal < 1.0 || BigDecimal(goal).scale > 2 =>
        F.pure(ErrorInfo.ValidationError(s"goal amount should be a valid monetary amount [$goal]").asLeft)
      case _ => fn
    }

  protected def withEnoughSavingsHeadroom[T](
    goal: Option[Double],
    acc:  HelpToSaveAccount
  )(fn:   => F[Result[T]]
  ): F[Result[T]] = {
    val maxGoal = acc.maximumPaidInThisMonth
    goal match {
      case Some(goal) if (goal > maxGoal) =>
        F.pure(ErrorInfo.ValidationError(s"goal amount should be in range 1 to $maxGoal").asLeft)
      case _ => fn
    }
  }

  override def getSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[SavingsGoal]]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error reading goal from events repo", savingsGoalEventRepo.getGoal(nino))
    }

  override def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Unit]] =
    withHelpToSaveAccount(nino) { acc =>
      trappingRepoExceptions("error writing to savings goal events repo",
                             savingsGoalEventRepo.deleteGoal(nino, acc.bonusTerms(1).bonusPaidOnOrAfterDate))
    }

  /**
    * Check if the nino has an NS&I help-to-save account associated with it. If so, run the supplied function on it,
    * otherwise map to an appropriate ErrorInfo value.
    */
  protected def withHelpToSaveAccount[T](
    nino:        Nino
  )(f:           HelpToSaveAccount => F[Result[T]]
  )(implicit hc: HeaderCarrier
  ): F[Result[T]] =
    helpToSaveGetAccount.getAccount(nino).flatMap {
      case Right(Some(account)) => f(account)
      case Right(None)          => F.pure(ErrorInfo.AccountNotFound.asLeft)
      case Left(errorInfo)      => F.pure(errorInfo.asLeft)
    }

  protected def trappingRepoExceptions[T](
    msg: String,
    f:   => F[T]
  ): F[Result[T]] =
    f.map(_.asRight[ErrorInfo]).recover {
      case NonFatal(t) =>
        logger.error(msg, t)
        ErrorInfo.General.asLeft[T]
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
              _ <- if (milestonesConfig.balanceMilestoneCheckEnabled)
                    balanceMilestonesService.balanceMilestoneCheck(nino,
                                                                   account.balance,
                                                                   account.bonusTerms(1).bonusPaidByDate)
                  else F.pure(())
              _ <- if (milestonesConfig.bonusPeriodMilestoneCheckEnabled)
                    bonusPeriodMilestonesService.bonusPeriodMilestoneCheck(nino,
                                                                           account.bonusTerms,
                                                                           account.balance,
                                                                           account.currentBonusTerm,
                                                                           account.isClosed)
                  else F.pure(())
              _ <- if (milestonesConfig.bonusReachedMilestoneCheckEnabled)
                    bonusReachedMilestonesService.bonusReachedMilestoneCheck(nino,
                                                                             account.bonusTerms,
                                                                             account.currentBonusTerm)
                  else F.pure(())
              _ <- mongoUpdateService
                    .updateExpireAtByNino(nino, account.bonusTerms(1).bonusPaidByDate.plusMonths(6).atStartOfDay())
              potentialBonus <- getPotentialBonus(nino, account)
            } yield Some(account.copy(potentialBonus = potentialBonus)))
          case _ => EitherT.rightT[F, ErrorInfo](Option.empty[Account])
        }
      case false =>
        EitherT.rightT[F, ErrorInfo](Option.empty[Account])
    }.value

  protected def fetchAccountWithGoal(nino: Nino)(implicit hc: HeaderCarrier): F[Result[Option[Account]]] =
    (
      helpToSaveGetAccount.getAccount(nino),
      fetchSavingsGoal(nino)
    ).mapN {
      case (Right(Some(account)), Right(goal)) =>
        Some(
          Account(account,
                  inAppPaymentsEnabled = config.inAppPaymentsEnabled,
                  savingsGoalsEnabled  = config.savingsGoalsEnabled,
                  logger,
                  LocalDate.now(),
                  goal)
        ).asRight

      case (Left(errorInfo), _) => errorInfo.asLeft
      case (_, Left(errorInfo)) => errorInfo.asLeft

      case (Right(None), _) =>
        logger.warn(
          s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
        )
        None.asRight
    }

  protected def fetchSavingsGoal(nino: Nino): F[Result[Option[SavingsGoal]]] =
    savingsGoalEventRepo.getGoal(nino).map(_.asRight[ErrorInfo]).recover {
      case t =>
        logger.warn("call to repo to retrieve savings goal failed", t)
        ErrorInfo.General.asLeft[Option[SavingsGoal]]
    }

  private def getPotentialBonus(
    nino:        Nino,
    account:     Account
  )(implicit hc: HeaderCarrier
  ): F[Option[BigDecimal]] =
    if (YearMonth.now().isBefore(account.openedYearMonth.plusMonths(3))) F.pure(None)
    else if (account.balance == BigDecimal(0.0) && account.highestBalance == 0)
      F.pure(Some(BigDecimal(savingsUpdateService.calculatePotentialBonus(5, account).getOrElse(0.0))))
    else if (savingsUpdateService.calculateMaxBonus(account).contains(BigDecimal(0.0))) F.pure(Some(0))
    else {
      helpToSaveGetTransactions.getTransactions(nino).map {
        case Right(foundTransactions) =>
          val reportStartDate = savingsUpdateService.calculateReportStartDate(account.openedYearMonth)
          val reportTransactions: Seq[Transaction] = foundTransactions.transactions.filter(transaction =>
            transaction.transactionDate.isAfter(reportStartDate.minusDays(1)) && transaction.transactionDate.isBefore(
              LocalDate.now().`with`(TemporalAdjusters.firstDayOfMonth())
            )
          )
          val averageSavingRate =
            savingsUpdateService.calculateAverageSavingRate(reportTransactions, reportStartDate)

          if (averageSavingRate > 50) {
            logger.warn(
              s"Average saving rate above 50!: $averageSavingRate. " +
              s"\nAccount startDate: ${account.openedYearMonth} " +
              s"\nReport startDate: $reportStartDate " +
              s"\nReport endDate: ${LocalDate.now().`with`(TemporalAdjusters.firstDayOfMonth())} " +
              s"\nTransactions: $reportTransactions"
            )
            None
          } else if (averageSavingRate > 0) {
            Some(
              savingsUpdateService
                .calculatePotentialBonus(averageSavingRate, account)
                .map(BigDecimal(_).setScale(2, BigDecimal.RoundingMode.HALF_UP))
                .getOrElse(0.0)
            )
          } else {
            account.currentBonusTerm match {
              case CurrentBonusTerm.First          => Some(account.bonusTerms.head.bonusEstimate)
              case CurrentBonusTerm.Second         => Some(account.bonusTerms.last.bonusEstimate)
              case CurrentBonusTerm.AfterFinalTerm => None
            }
          }
        case _ => None
      }

    }
}
