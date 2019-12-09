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
import uk.gov.hmrc.mobilehelptosave.domain.Milestones
import uk.gov.hmrc.mobilehelptosave.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobilehelptosave.services.MilestonesService
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

trait MilestonesActions {
  def getMilestones(nino: Nino, journeyId: JourneyId): Action[AnyContent]

  def markAsSeen(nino: Nino, milestoneId: String, journeyId: JourneyId): Action[AnyContent]
}

class MilestonesController(
  val logger:               LoggerLike,
  milestonesService:        MilestonesService[Future],
  authorisedWithIds:        AuthorisedWithIds,
  val controllerComponents: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendBaseController
    with ControllerChecks
    with MilestonesActions {

  override def getMilestones(nino: Nino, journeyId: JourneyId): Action[AnyContent] = authorisedWithIds.async {
    implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino, request.shuttered) { nino =>
        milestonesService
          .getMilestones(nino)
          .map(milestones => Ok(Json.toJson(Milestones(milestones))))
      }
  }

  override def markAsSeen(nino: Nino, milestoneType: String, journeyId: JourneyId): Action[AnyContent] = authorisedWithIds.async {
    implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino, request.shuttered) { nino =>
        milestonesService.markAsSeen(nino, milestoneType).map(_ => NoContent)
      }
  }

}
