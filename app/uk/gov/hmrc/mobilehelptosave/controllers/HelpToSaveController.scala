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

import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.domain.Nino
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
    verifyingMatchingNino(config.shuttering, ninoString) { nino =>
      accountService.account(nino).map {
        case Left(errorInfo)      => InternalServerError(Json.toJson(errorInfo))
        case Right(None)          => AccountNotFound
        case Right(Some(account)) => Ok(Json.toJson(account))
      }
    }
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
        .setGoal(SavingsGoalMongoModel(verifiedUserNino, request.body.goalAmount, LocalDateTime.now))
        .recover {
          case t => logger.error("error writing savings goal to mongo", t)
        }
        .map(_ => NoContent)
  }

  def deleteSavingsGoal(nino: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(config.shuttering, nino) { nino =>
        savingsGoalRepo.delete(nino).map(_ => NoContent)
      }
    }
}
