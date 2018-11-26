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

package uk.gov.hmrc.mobilehelptosave.controllers


import java.time.LocalDateTime

import cats.instances.future._
import cats.syntax.apply._
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalMongoModel, SavingsGoalRepo}
import uk.gov.hmrc.mobilehelptosave.services.AccountService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

trait HelpToSaveActions {
  def getTransactions(ninoString: String): Action[AnyContent]
  def getAccount(ninoString: String): Action[AnyContent]
  def putSavingsGoal(ninoString: String): Action[SavingsGoal]
  def deleteSavingsGoal(ninoString: String): Action[AnyContent]
}

@Singleton
class HelpToSaveController @Inject()
(
  val logger: LoggerLike,
  accountService: AccountService,
  helpToSaveGetTransactions: HelpToSaveGetTransactions,
  authorisedWithIds: AuthorisedWithIds,
  config: HelpToSaveControllerConfig,
  savingsGoalRepo: SavingsGoalRepo
)(implicit ec: ExecutionContext) extends BaseController with ControllerChecks with HelpToSaveActions {

  private final val AccountNotFound = NotFound(Json.toJson(ErrorBody("ACCOUNT_NOT_FOUND", "No Help to Save account exists for the specified NINO")))

  def getTransactions(ninoString: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(config.shuttering, ninoString) { verifiedUserNino =>
        helpToSaveGetTransactions.getTransactions(verifiedUserNino).map {
          case Right(Some(transactions)) => Ok(Json.toJson(transactions.reverse))
          case Right(None)               => AccountNotFound
          case Left(errorInfo)           => InternalServerError(Json.toJson(errorInfo))
        }
      }
    }

  def getAccount(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    verifyingMatchingNino(config.shuttering, ninoString)(fetchAccountDetails)
  }

  private def fetchAccountDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[Result] = {
    // Use an applicative approach here as the two requests are independent of each other and can run concurrently.
    // `mapN` is not inherently parallel, but because the `Future`s run eagerly when created they do end up running
    // in parallel.
    (
      fetchSavingsGoal(nino),
      accountService.account(nino)
    ).mapN {
      case (goal, Right(Some(account))) =>
        val savingsGoal = goal.map(t => SavingsGoal(t.amount))
        Ok(Json.toJson(account.copy(savingsGoal = savingsGoal, savingsGoalsEnabled = config.savingsGoalsEnabled)))

      case (_, Right(None))     => AccountNotFound
      case (_, Left(errorInfo)) => InternalServerError(Json.toJson(errorInfo))
    }
  }

  /**
    * If there is an error talking to mongo then this function will recover the `Future` to a `Success(None)` on
    * the basis that we don't want to stop the user seeing their other account details just because something
    * went wrong trying to look up their goal. Other options are to convert the failure to an `ErrorInfo` and
    * return a `Future[Either[ErrorInfo, Option[SavingsGoalMongoModel]]]`, which would let the api call fail with
    * a meaningful error, or to expand the type returned to the api caller to give the client enough information
    * to tell the user something useful (e.g. "We can't display your goal at the moment, but here's the rest of
    * your account details").
    */
  private def fetchSavingsGoal(nino: Nino)(implicit ec: ExecutionContext): Future[Option[SavingsGoalMongoModel]] =
    savingsGoalRepo.get(nino).recover {
      case t =>
        logger.warn("call to mongo to retrieve savings goal failed", t)
        None
    }

  def putSavingsGoal(ninoString: String): Action[SavingsGoal] =
    authorisedWithIds.async(parse.json[SavingsGoal]) { implicit request: RequestWithIds[SavingsGoal] =>
      verifyingMatchingNino(config.shuttering, ninoString) { verifiedUserNino =>
        accountService.account(verifiedUserNino).flatMap {
          case Right(None) =>
            Future.successful(AccountNotFound)

          case Right(Some(acc)) =>
            updateSavingsGoal(verifiedUserNino, acc.maximumPaidInThisMonth)

          case Left(errorInfo) =>
            Future.successful(InternalServerError(Json.toJson(errorInfo)))
        }
      }
    }

  private def updateSavingsGoal(verifiedUserNino: Nino, maxGoal: BigDecimal)(implicit request: RequestWithIds[SavingsGoal]): Future[Result] = {
    if (request.body.goalAmount < 1.0 || request.body.goalAmount > maxGoal)
      Future.successful(UnprocessableEntity(obj("error" -> s"goal amount should be in range 1 to $maxGoal")))
    else
      savingsGoalRepo
        .put(SavingsGoalMongoModel(verifiedUserNino.nino, request.body.goalAmount, LocalDateTime.now))
        .recover {
          case t => logger.error("error writing savings goal to mongo", t)
        }
        .map(_ => NoContent)
  }

  def deleteSavingsGoal(nino: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(config.shuttering, nino) {
        savingsGoalRepo.delete(_).map(_ => NoContent)
      }
    }
}
