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

import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.mobilehelptosave.domain.{DisabledStartupResponse, EnabledStartupResponse}
import uk.gov.hmrc.mobilehelptosave.services.UserService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

@Singleton()
class StartupController @Inject() (
  userService: UserService,
  authorisedWithIds: AuthorisedWithIds,
  @Named("helpToSave.enabled") helpToSaveEnabled: Boolean,
  @Named("helpToSave.balanceEnabled") balanceEnabled: Boolean,
  @Named("helpToSave.paidInThisMonthEnabled") paidInThisMonthEnabled: Boolean,
  @Named("helpToSave.firstBonusEnabled") firstBonusEnabled: Boolean,
  @Named("helpToSave.shareInvitationEnabled") shareInvitationEnabled: Boolean,
  @Named("helpToSave.savingRemindersEnabled") savingRemindersEnabled: Boolean,
  @Named("helpToSave.infoUrl") helpToSaveInfoUrl: String,
  @Named("helpToSave.invitationUrl") helpToSaveInvitationUrl: String,
  @Named("helpToSave.accessAccountUrl") helpToSaveAccessAccountUrl: String
) extends BaseController {

  val startup: Action[AnyContent] = authorisedWithIds.async { implicit request =>
    val responseF = if (helpToSaveEnabled) {
      userService.userDetails(request.internalAuthId, request.nino).map { user =>
        EnabledStartupResponse(
          infoUrl = helpToSaveInfoUrl,
          invitationUrl = helpToSaveInvitationUrl,
          accessAccountUrl = helpToSaveAccessAccountUrl,
          user = user,
          balanceEnabled = balanceEnabled,
          paidInThisMonthEnabled = paidInThisMonthEnabled,
          firstBonusEnabled = firstBonusEnabled,
          shareInvitationEnabled = shareInvitationEnabled,
          savingRemindersEnabled = savingRemindersEnabled
        )
      }
    } else {
      Future successful DisabledStartupResponse
    }

    responseF.map(response => Ok(Json.toJson(response)))
  }

}
