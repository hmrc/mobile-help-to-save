/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalDateTime

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.apply._
import cats.syntax.either._
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.AccountServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveAccountService])
trait AccountService {
  type Result[T] = Either[ErrorInfo, T]

  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Option[Account]]]

  def setSavingsGoal(nino: Nino, savingsGoal: SavingsGoal)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Unit]]
  def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Unit]]

  def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[List[SavingsGoalEvent]]]
}

@Singleton
class HelpToSaveAccountService @Inject()(
  logger: LoggerLike,
  helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
  helpToSaveGetAccount: HelpToSaveGetAccount,
  config: AccountServiceConfig,
  savingsGoalEventRepo: SavingsGoalEventRepo
) extends AccountService {

  override def setSavingsGoal(nino: Nino, savingsGoal: SavingsGoal)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Unit]] =
    withValidSavingsAmount(savingsGoal.goalAmount) {
      withHelpToSaveAccount(nino) { acc: Account =>
        withEnoughSavingsHeadroom(savingsGoal.goalAmount, acc) {
          trappingRepoExceptions("error writing savings goal to repo", savingsGoalEventRepo.setGoal(nino, savingsGoal.goalAmount))
        }
      }
    }

  override def deleteSavingsGoal(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Unit]] =
    withHelpToSaveAccount(nino) { _ =>
      trappingRepoExceptions("error writing to savings goal events repo", savingsGoalEventRepo.deleteGoal(nino))
    }

  override def savingsGoalEvents(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[List[SavingsGoalEvent]]] =
    withHelpToSaveAccount(nino) { _ => trappingRepoExceptions("error reading from savings goal events repo", savingsGoalEventRepo.getEvents(nino)) }

  private def trappingRepoExceptions[T](msg: String, f: => Future[T])(implicit ec: ExecutionContext): Future[Result[T]] =
    f.map(_.asRight[ErrorInfo]).recover {
      case NonFatal(t) =>
        logger.error(msg, t)
        ErrorInfo.General.asLeft[T]
    }

  /**
    * Check if the nino has an NS&I account associated with it. If so, run the supplied function on it, otherwise map
    * to an appropriate ErrorInfo value
    */
  private def withHelpToSaveAccount[T](nino: Nino)(f: Account => Future[Result[T]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[T]] =
    fetchNSAndIAccount(nino).flatMap {
      case Right(Some(account)) => f(account)
      case Right(None)          => Future.successful(ErrorInfo.AccountNotFound.asLeft)
      case Left(errorInfo)      => Future.successful(errorInfo.asLeft)
    }

  private def withValidSavingsAmount[T](goal: Double)(fn: => Future[Result[T]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[T]] = {
    if (goal < 1.0 || BigDecimal(goal).scale > 2)
      Future.successful(ErrorInfo.ValidationError(s"goal amount should be a valid monetary amount [$goal]").asLeft)
    else
      fn
  }

  private def withEnoughSavingsHeadroom[T](goal: Double, acc:Account)(fn: => Future[Result[T]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[T]] = {
    val maxGoal = acc.maximumPaidInThisMonth
    if (goal > maxGoal)
      Future.successful(ErrorInfo.ValidationError(s"goal amount should be in range 1 to $maxGoal").asLeft)
    else
      fn
  }

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Option[Account]]] =
    EitherT(helpToSaveEnrolmentStatus.enrolmentStatus()).flatMap {
      case true =>
        EitherT(fetchAccountAndGoal(nino))

      case false =>
        EitherT.rightT[Future, ErrorInfo](Option.empty[Account])
    }.value

  private def fetchAccountAndGoal(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Option[Account]]] = {
    // Use an applicative approach here as the two requests are independent of each other and can run concurrently.
    // `mapN` is not inherently parallel, but because the `Future`s run eagerly when created they do end up running
    // in parallel.
    (
      fetchSavingsGoal(nino),
      fetchNSAndIAccount(nino)
    ).mapN {
      case (Right(goal), Right(Some(account))) =>
        val savingsGoal = goal.map(t => SavingsGoal(t.amount))
        Some(account.copy(savingsGoal = savingsGoal, savingsGoalsEnabled = config.savingsGoalsEnabled)).asRight

      case (_, Left(errorInfo)) => errorInfo.asLeft
      case (Left(errorInfo), _) => errorInfo.asLeft

      case (_, Right(None)) => None.asRight
    }
  }

  private def fetchNSAndIAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result[Option[Account]]] =
    EitherT(helpToSaveGetAccount.getAccount(nino)).map {
      case Some(helpToSaveAccount) =>
        Some(Account(helpToSaveAccount, inAppPaymentsEnabled = config.inAppPaymentsEnabled, logger, LocalDate.now()))
      case None                    =>
        logger.warn(s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")
        None
    }.value


  private val localDateTimeOrdering: Ordering[LocalDateTime] = new Ordering[LocalDateTime] {
    override def compare(x: LocalDateTime, y: LocalDateTime): Int = x.compareTo(y)
  }

  private def fetchSavingsGoal(nino: Nino)(implicit ec: ExecutionContext): Future[Result[Option[SavingsGoalRepoModel]]] = {
    savingsGoalEventRepo.getEvents(nino).map(_.sortBy(_.date)(localDateTimeOrdering.reverse)).map {
      case List()                                    => None.asRight[ErrorInfo]
      case SavingsGoalDeleteEvent(_, _) :: _         => None.asRight[ErrorInfo]
      case SavingsGoalSetEvent(_, amount, date) :: _ => Some(SavingsGoalRepoModel(nino, amount, date)).asRight[ErrorInfo]
    }.recover {
      case t =>
        logger.warn("call to repo to retrieve savings goal failed", t)
        ErrorInfo.General.asLeft[Option[SavingsGoalRepoModel]]
    }
  }

}
