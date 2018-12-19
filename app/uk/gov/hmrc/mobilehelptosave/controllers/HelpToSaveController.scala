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

import play.api.LoggerLike
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.AccountService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext

trait HelpToSaveActions {
  def getTransactions(ninoString: String): Action[AnyContent]
  def getAccount(ninoString: String): Action[AnyContent]
  def putSavingsGoal(ninoString: String): Action[SavingsGoal]
  def deleteSavingsGoal(ninoString: String): Action[AnyContent]
  def getSavingsGoalsEvents(nino: String): Action[AnyContent]
}

class HelpToSaveController
(
  val logger: LoggerLike,
  accountService: AccountService,
  helpToSaveGetTransactions: HelpToSaveGetTransactions,
  authorisedWithIds: AuthorisedWithIds,
  config: HelpToSaveControllerConfig
)(implicit ec: ExecutionContext) extends BaseController with ControllerChecks with HelpToSaveActions {
  override def shuttering: Shuttering = config.shuttering

  private def orAccountNotFound[T: Writes](o: Option[T]): Result =
    o.fold(AccountNotFound)(v => Ok(Json.toJson(v)))

  override def getTransactions(ninoString: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(ninoString) { verifiedUserNino =>
        helpToSaveGetTransactions.getTransactions(verifiedUserNino).map(handlingErrors(txs => Ok(Json.toJson(txs.reverse))))
      }
    }

  override def getAccount(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    verifyingMatchingNino(ninoString) { nino =>
      //noinspection ConvertibleToMethodValue
      accountService.account(nino).map(handlingErrors(orAccountNotFound(_)))
    }
  }

  override def putSavingsGoal(ninoString: String): Action[SavingsGoal] =
    authorisedWithIds.async(parse.json[SavingsGoal]) { implicit request: RequestWithIds[SavingsGoal] =>
      verifyingMatchingNino(ninoString) { verifiedUserNino =>
        accountService.setSavingsGoal(verifiedUserNino, request.body).map(handlingErrors(_ => NoContent))
      }
    }

  override def deleteSavingsGoal(nino: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino) { verifiedNino =>
        accountService.deleteSavingsGoal(verifiedNino).map(handlingErrors(_ => NoContent))
      }
    }

  override def getSavingsGoalsEvents(nino: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino) { verifiedNino =>
        accountService.savingsGoalEvents(verifiedNino).map {
          handlingErrors(events => Ok(Json.toJson(events)))
        }
      }
    }
}
