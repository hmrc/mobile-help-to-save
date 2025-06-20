/*
 * Copyright 2023 HM Revenue & Customs
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
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.apache.pekko.pattern.FutureRef
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.{AccountServiceConfig, MilestonesConfig}
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount, HelpToSaveGetTransactions}
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mobilehelptosave.repository.*

import java.time.temporal.TemporalAdjusters
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait AccountService {
  type Result[T] = Either[ErrorInfo, T]

  def account(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Option[Account]]]

  def setSavingsGoal(
    nino: Nino,
    savingsGoal: SavingsGoal
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Unit]]

  def getSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Option[SavingsGoal]]]
  def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Unit]]

  def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Seq[SavingsGoalEvent]]]
}

class HtsAccountService(
  logger: LoggerLike,
  config: AccountServiceConfig,
  helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
  helpToSaveGetAccount: HelpToSaveGetAccount,
  savingsGoalEventRepo: SavingsGoalEventRepo,
  balanceMilestonesService: BalanceMilestonesService,
  bonusPeriodMilestonesService: BonusPeriodMilestonesService,
  bonusReachedMilestonesService: BonusReachedMilestonesService,
  mongoUpdateService: MongoUpdateService,
  savingsUpdateService: SavingsUpdateService,
  helpToSaveGetTransactions: HelpToSaveGetTransactions,
  milestonesConfig: MilestonesConfig
) extends AccountService {

  override def setSavingsGoal(
    nino: Nino,
    savingsGoal: SavingsGoal
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Unit]] =
    withValidSavingsAmount(savingsGoal.goalAmount) {
      withHelpToSaveAccount(nino) { acc =>
        withEnoughSavingsHeadroom(savingsGoal.goalAmount, acc) {
          trappingRepoExceptions(
            "error writing savings goal to repo",
            savingsGoalEventRepo.setGoal(nino, savingsGoal.goalAmount, savingsGoal.goalName, acc.bonusTerms(1).bonusPaidOnOrAfterDate)
          )
        }
      }
    }

  protected def withValidSavingsAmount[T](goal: Option[Double])(fn: => Future[Result[T]]): Future[Result[T]] =
    goal match {
      case Some(goal) if goal < 1.0 || BigDecimal(goal).scale > 2 =>
        Future.successful(ErrorInfo.ValidationError(s"goal amount should be a valid monetary amount [$goal]").asLeft)
      case _ => fn
    }

  protected def withEnoughSavingsHeadroom[T](
    goal: Option[Double],
    acc: HelpToSaveAccount
  )(fn: => Future[Result[T]]): Future[Result[T]] = {
    val maxGoal = acc.maximumPaidInThisMonth
    goal match {
      case Some(goal) if goal > maxGoal =>
        Future.successful(ErrorInfo.ValidationError(s"goal amount should be in range 1 to $maxGoal").asLeft)
      case _ => fn
    }
  }

  override def getSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Option[SavingsGoal]]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error reading goal from events repo", savingsGoalEventRepo.getGoal(nino))
    }

  override def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Unit]] =
    withHelpToSaveAccount(nino) { acc =>
      trappingRepoExceptions("error writing to savings goal events repo",
                             savingsGoalEventRepo.deleteGoal(nino, acc.bonusTerms(1).bonusPaidOnOrAfterDate)
                            )
    }

  /** Check if the nino has an NS&I help-to-save account associated with it. If so, run the supplied function on it, otherwise map to an appropriate
    * ErrorInfo value.
    */
  protected def withHelpToSaveAccount[T](
    nino: Nino
  )(f: HelpToSaveAccount => Future[Result[T]])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[T]] =
    helpToSaveGetAccount.getAccount(nino).flatMap {
      case Right(Some(account)) => f(account)
      case Right(None)          => Future.successful(ErrorInfo.AccountNotFound.asLeft)
      case Left(errorInfo)      => Future.successful(errorInfo.asLeft)
    }

  protected def trappingRepoExceptions[T](
    msg: String,
    f: => Future[T]
  )(implicit ex: ExecutionContext): Future[Result[T]] =
    f.map(_.asRight[ErrorInfo]).recover { case NonFatal(t) =>
      logger.error(msg, t)
      ErrorInfo.General.asLeft[T]
    }

  override def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Seq[SavingsGoalEvent]]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error reading from savings goal events repo", savingsGoalEventRepo.getEvents(nino))
    }

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] = {
    helpToSaveEnrolmentStatus.enrolmentStatus().flatMap {
      case Left(error) => Future.successful(Left(error))

      case Right(false) => Future.successful(Right(None))

      case Right(true) =>
        fetchAccountWithGoal(nino).flatMap {
          case Left(error) => Future.successful(Left(error))

          case Right(None) => Future.successful(Right(None))

          case Right(Some(account)) =>
            val updateAndChecks: Future[Unit] = for {
              _ <- mongoUpdateService.updateExpireAtByNino(
                     nino,
                     account.bonusTerms(1).bonusPaidByDate.plusMonths(6).atStartOfDay()
                   )

              _ <- if (milestonesConfig.balanceMilestoneCheckEnabled)
                     balanceMilestonesService
                       .balanceMilestoneCheck(nino, account.balance, account.bonusTerms(1).bonusPaidByDate)
                   else Future.successful(())

              _ <- if (milestonesConfig.bonusPeriodMilestoneCheckEnabled)
                     bonusPeriodMilestonesService
                       .bonusPeriodMilestoneCheck(nino, account.bonusTerms, account.balance, account.currentBonusTerm, account.isClosed)
                   else Future.successful(())

              _ <- if (milestonesConfig.bonusReachedMilestoneCheckEnabled)
                     bonusReachedMilestonesService
                       .bonusReachedMilestoneCheck(nino, account.bonusTerms, account.currentBonusTerm)
                   else Future.successful(())
            } yield ()

            updateAndChecks.flatMap { _ =>
              getPotentialBonus(nino, account).map { potentialBonus =>
                Right(Some(account.copy(potentialBonus = potentialBonus)))
              }
            }
        }
    }
  }

  protected def fetchAccountWithGoal(nino: Nino)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Result[Option[Account]]] =
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
                  goal
                 )
        ).asRight

      case (Left(errorInfo), _) => errorInfo.asLeft
      case (_, Left(errorInfo)) => errorInfo.asLeft

      case (Right(None), _) =>
        logger.warn(
          s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
        )
        None.asRight
    }

  protected def fetchSavingsGoal(nino: Nino)(implicit ex: ExecutionContext): Future[Result[Option[SavingsGoal]]] =
    savingsGoalEventRepo.getGoal(nino).map(_.asRight[ErrorInfo]).recover { case t =>
      logger.warn("call to repo to retrieve savings goal failed", t)
      ErrorInfo.General.asLeft[Option[SavingsGoal]]
    }

  private def getPotentialBonus(
    nino: Nino,
    account: Account
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[BigDecimal]] =
    if (YearMonth.now().isBefore(account.openedYearMonth.plusMonths(3))) Future.successful(None)
    else if (account.balance == BigDecimal(0.0) && account.highestBalance == 0)
      Future.successful(Some(BigDecimal(savingsUpdateService.calculatePotentialBonus(5, account).getOrElse(0.0))))
    else if (savingsUpdateService.calculateMaxBonus(account).contains(BigDecimal(0.0))) Future.successful(Some(0))
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
