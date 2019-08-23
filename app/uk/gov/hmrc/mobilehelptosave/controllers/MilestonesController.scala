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
import uk.gov.hmrc.mobilehelptosave.config.MilestonesControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain.{Milestones, Shuttering}
import uk.gov.hmrc.mobilehelptosave.services.MilestonesService
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

trait MilestonesActions {
  def getMilestones(ninoString: String, journeyId: String): Action[AnyContent]

  def markAsSeen(ninoString: String, milestoneId: String, journeyId: String): Action[AnyContent]
}

class MilestonesController(
  val logger:               LoggerLike,
  milestonesService:        MilestonesService[Future],
  authorisedWithIds:        AuthorisedWithIds,
  config:                   MilestonesControllerConfig,
  val controllerComponents: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendBaseController
    with ControllerChecks
    with MilestonesActions {

  override def shuttering: Shuttering = config.shuttering

  override def getMilestones(ninoString: String, journeyId: String): Action[AnyContent] = authorisedWithIds.async {
    implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(ninoString) { nino =>
        milestonesService
          .getMilestones(nino)
          .map(milestones => Ok(Json.toJson(Milestones(milestones))))
      }
  }

  override def markAsSeen(ninoString: String, milestoneType: String, journeyId: String): Action[AnyContent] = authorisedWithIds.async {
    implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(ninoString) { nino =>
        milestonesService.markAsSeen(nino, milestoneType).map(_ => NoContent)
      }
  }

}
