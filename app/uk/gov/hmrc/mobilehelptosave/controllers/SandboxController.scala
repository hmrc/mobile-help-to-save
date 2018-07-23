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
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.sandbox.SandboxData
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future

@Singleton
class SandboxController @Inject()(
   logger: LoggerLike,
   config: HelpToSaveControllerConfig,
   sandboxData: SandboxData
 ) extends BaseController with ControllerChecks {

  def getTransactions(ninoString: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future successful Ok(Json.toJson(
          sandboxData.transactions
        ))
      }
    }
  }

  def getAccount(ninoString: String): Action[AnyContent] = Action.async { implicit request =>
    withShuttering(config.shuttering) {
      withValidNino(ninoString) { _ =>
        Future successful Ok(Json.toJson(sandboxData.account))
      }
    }
  }
}