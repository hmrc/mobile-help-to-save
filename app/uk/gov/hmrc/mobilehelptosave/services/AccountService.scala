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

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.apply._
import cats.syntax.either._
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.AccountServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEventRepo, SavingsGoalMongoModel, SavingsGoalRepo}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveAccountService])
trait AccountService {
  def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]]
}

@Singleton
class HelpToSaveAccountService @Inject()(
  logger: LoggerLike,
  helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
  helpToSaveGetAccount: HelpToSaveGetAccount,
  config: AccountServiceConfig,
  savingsGoalRepo: SavingsGoalRepo,
  savingsGoalEventRepo: SavingsGoalEventRepo
) extends AccountService {

  override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
    EitherT(helpToSaveEnrolmentStatus.enrolmentStatus()).flatMap {
      case true =>
        EitherT(fetchAccountAndGoal(nino))

      case false =>
        EitherT.rightT[Future, ErrorInfo](Option.empty[Account])
    }.value

  private def fetchAccountAndGoal(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] = {
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

  private def fetchNSAndIAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
    EitherT(helpToSaveGetAccount.getAccount(nino)).map {
      case Some(helpToSaveAccount) =>
        Some(Account(helpToSaveAccount, inAppPaymentsEnabled = config.inAppPaymentsEnabled, logger))
      case None                    =>
        logger.warn(s"$nino was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent")
        None
    }.value


  private def fetchSavingsGoal(nino: Nino)(implicit ec: ExecutionContext): Future[Either[ErrorInfo, Option[SavingsGoalMongoModel]]] =
    savingsGoalRepo.get(nino).map(_.asRight[ErrorInfo]).recover {
      case t =>
        logger.warn("call to mongo to retrieve savings goal failed", t)
        ErrorInfo.General.asLeft[Option[SavingsGoalMongoModel]]
    }

}
