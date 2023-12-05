/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.api

import controllers.Assets
import play.api.http.HttpErrorHandler
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.mobilehelptosave.config.DocumentationControllerConfig
import uk.gov.hmrc.mobilehelptosave.views.txt

import javax.inject.Inject
import scala.collection.mutable

case class ApiAccess(
  `type`:                    String,
  whitelistedApplicationIds: mutable.Buffer[String])

object ApiAccess {
  implicit val writes: OWrites[ApiAccess] = Json.writes[ApiAccess]
}

class DocumentationController @Inject()(
  errorHandler: HttpErrorHandler,
  config:       DocumentationControllerConfig,
  cc:           ControllerComponents,
  assets:       Assets)
    extends uk.gov.hmrc.api.controllers.DocumentationController(cc, assets, errorHandler) {

  private lazy val apiAccess = ApiAccess(config.apiAccessType, config.apiWhiteListApplicationIds)

  override def definition(): Action[AnyContent] = Action {
    Ok(txt.definition(apiAccess)).as(JSON)
  }
}
