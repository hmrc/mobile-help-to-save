/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.controllers.test

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.TestSavingsGoal
import uk.gov.hmrc.mobilehelptosave.repository.{MilestonesRepo, PreviousBalanceRepo, SavingsGoalEventRepo}
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestController(
  savingsGoalEventRepo:     SavingsGoalEventRepo[Future],
  milestonesRepo:           MilestonesRepo[Future],
  previousBalanceRepo:      PreviousBalanceRepo[Future],
  val controllerComponents: ControllerComponents)
    extends BackendBaseController {

  def clearGoalEvents(): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.clearGoalEvents().map {
      case true => Ok("Successfully cleared goal events")
      case _    => InternalServerError("Failed to clear goal events")
    }
  }

  def getGoalEvents(nino: Nino): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.getEvents(nino).map { events =>
      Ok(Json.toJson(events))
    }
  }

  def clearMilestoneData(): Action[AnyContent] = Action.async {
    for {
      _ <- previousBalanceRepo.clearPreviousBalance()
      _ <- milestonesRepo.clearMilestones()
    } yield Ok("Successfully cleared all milestone data")
  }

  def putSavingsGoal: Action[TestSavingsGoal] = Action.async(parse.json[TestSavingsGoal]) {
    implicit request: Request[TestSavingsGoal] =>
      savingsGoalEventRepo.setGoal(request.body.nino, request.body.goalAmount, request.body.goalName, request.body.date)
      Future successful Created("Goal successfully created")
  }

}
