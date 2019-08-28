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
import uk.gov.hmrc.mobilehelptosave.connectors.ShutteringConnector
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.Future

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
  override def shuttering: Shuttering = config.shuttering

  override def getTransactions(ninoString: String, journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future successful Ok(
          Json.toJson(
            sandboxData.transactions
          ))
      }
    }
  }

  override def getAccount(ninoString: String, journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future successful Ok(Json.toJson(sandboxData.account))
      }
    }
  }

  override def putSavingsGoal(ninoString: String, journeyId: String): Action[SavingsGoal] =
    Action.async(parse.json[SavingsGoal]) { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withValidNino(ninoString) { _ =>
            Future.successful(NoContent)
          }
        }
      }
    }

  override def deleteSavingsGoal(ninoString: String, journeyId: String): Action[AnyContent] =
    Action.async { implicit request =>
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          withValidNino(ninoString) { _ =>
            Future.successful(NoContent)
          }
        }
      }
    }

  override def getMilestones(ninoString: String, journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future successful Ok(Json.toJson(sandboxData.milestones))
      }
    }
  }

  override def markAsSeen(ninoString: String, milestoneId: String, journeyId: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future.successful(NoContent)
      }
    }
  }
}
