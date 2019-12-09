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

package uk.gov.hmrc.mobilehelptosave.controllers

import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.connectors.ShutteringConnector
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal
import uk.gov.hmrc.mobilehelptosave.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

class SandboxController(
  val logger:                    LoggerLike,
  shutteringConnector:           ShutteringConnector,
  sandboxData:                   SandboxData,
  val controllerComponents:      ControllerComponents
)(implicit val executionContext: ExecutionContext)
    extends BackendBaseController
    with ControllerChecks
    with HelpToSaveActions
    with MilestonesActions {

  override def getTransactions(nino: Nino, journeyId: JourneyId): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future successful Ok(
            Json.toJson(
              sandboxData.transactions
            ))
        }
      }
    }

  override def getAccount(nino: Nino, journeyId: JourneyId): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future successful Ok(Json.toJson(sandboxData.account))
        }
      }
    }

  override def putSavingsGoal(nino: Nino, journeyId: JourneyId): Action[SavingsGoal] =
    Action.async(parse.json[SavingsGoal]) { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future.successful(NoContent)
        }
      }
    }

  override def deleteSavingsGoal(nino: Nino, journeyId: JourneyId): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future.successful(NoContent)
        }
      }
    }

  override def getMilestones(nino: Nino, journeyId: JourneyId): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future successful Ok(Json.toJson(sandboxData.milestones))
        }
      }
    }

  override def markAsSeen(nino: Nino, milestoneId: String, journeyId: JourneyId): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId.value).flatMap { shuttered =>
        withShuttering(shuttered) {
          Future.successful(NoContent)
        }
      }
    }

}
