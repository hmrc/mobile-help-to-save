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
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import uk.gov.hmrc.mobilehelptosave.services.MessagesService
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

trait MessagesActions {
  def getMessages(ninoString: String): Action[AnyContent]

  def markAsSeen(ninoString: String, messageId: String): Action[AnyContent]
}

class MessagesController(
  val logger:               LoggerLike,
  messagesService:          MessagesService[Future],
  authorisedWithIds:        AuthorisedWithIds,
  config:                   HelpToSaveControllerConfig,
  val controllerComponents: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendBaseController
    with ControllerChecks
    with MessagesActions {

  override def shuttering: Shuttering = config.shuttering

  override def getMessages(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    verifyingMatchingNino(ninoString) { nino => messagesService.getMessages(nino).map(messages => Ok(Json.toJson(messages.map(_.toApiMessage))))
    }
  }

  override def markAsSeen(ninoString: String, messageId: String): Action[AnyContent] = authorisedWithIds.async {
    implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(ninoString) { nino => messagesService.markAsSeen(messageId).map(_ => NoContent)
      }
  }

}
