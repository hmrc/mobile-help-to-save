/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.auth.core.Nino
import uk.gov.hmrc.mobilehelptosave.config.StartupControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.UserService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

class StartupController(
  userService:              UserService,
  authorisedWithIds:        AuthorisedWithIds,
  config:                   StartupControllerConfig,
  val controllerComponents: ControllerComponents
)(implicit ec:              ExecutionContext)
    extends BackendBaseController {

  val startup: Action[AnyContent] = {
    authorisedWithIds.async { implicit request =>
      if (!request.shuttered.shuttered) {
        val responseF = request.nino match {
          case Some(nino) =>
            userService.userDetails(nino).map { userOrError =>
              StartupResponse(
                shuttering       = request.shuttered,
                infoUrl          = Some(config.helpToSaveInfoUrl),
                infoUrlSso       = Some(config.helpToSaveInfoUrlSso),
                accessAccountUrl = Some(config.helpToSaveAccessAccountUrl),
                accountPayInUrl  = Some(config.helpToSaveAccountPayInUrl),
                user             = userOrError.toOption,
                userError        = userOrError.left.toOption
              )
            }
          case _ => throw new IllegalStateException("Unexpected state, Nino should exist")
        }

        responseF.map(response => Ok(Json.toJson(response)))

      } else {
        Future.successful(Ok(Json.toJson(StartupResponse.shutteredResponse(request.shuttered))))
      }
    }
  }
}
