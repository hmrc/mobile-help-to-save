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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.config.StartupControllerConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.services.UserService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

@Singleton()
class StartupController @Inject() (
  userService: UserService,
  authorisedWithIds: AuthorisedWithIds,
  config: StartupControllerConfig
) extends BaseController {

  val startup: Action[AnyContent] = if (config.helpToSaveEnabled && !config.shuttering.shuttered) {
    authorisedWithIds.async { implicit request =>
      val responseF = userService.userDetails(request.internalAuthId, request.nino).map { userOrError =>
        EnabledStartupResponse(
          shuttering = config.shuttering,
          infoUrl = Some(config.helpToSaveInfoUrl),
          invitationUrl = Some(config.helpToSaveInvitationUrl),
          accessAccountUrl = Some(config.helpToSaveAccessAccountUrl),
          user = userOrError.right.toOption.flatten,
          userError = userOrError.left.toOption,
          balanceEnabled = config.balanceEnabled,
          paidInThisMonthEnabled = config.paidInThisMonthEnabled,
          firstBonusEnabled = config.firstBonusEnabled,
          shareInvitationEnabled = config.shareInvitationEnabled,
          savingRemindersEnabled = config.savingRemindersEnabled,
          transactionsEnabled = config.transactionsEnabled
        )
      }
      responseF.map(response => Ok(Json.toJson(response)))
    }
  } else {
    Action { implicit request =>
      val response = if (config.helpToSaveEnabled) {
        EnabledStartupResponse(
          shuttering = config.shuttering,
          infoUrl = None,
          invitationUrl = None,
          accessAccountUrl = None,
          user = None,
          userError = None,
          balanceEnabled = config.balanceEnabled,
          paidInThisMonthEnabled = config.paidInThisMonthEnabled,
          firstBonusEnabled = config.firstBonusEnabled,
          shareInvitationEnabled = config.shareInvitationEnabled,
          savingRemindersEnabled = config.savingRemindersEnabled,
          transactionsEnabled = config.transactionsEnabled
        )
      } else {
        DisabledStartupResponse
      }

      Ok(Json.toJson(response))
    }
  }

}
