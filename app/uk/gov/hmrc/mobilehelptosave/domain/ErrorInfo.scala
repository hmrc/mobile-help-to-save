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

package uk.gov.hmrc.mobilehelptosave.domain

import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, OWrites}

sealed abstract class ErrorInfo(val code: String)

object ErrorInfo {
  object General extends ErrorInfo("GENERAL")

  object AccountNotFound extends ErrorInfo("ACCOUNT_NOT_FOUND") {
    val message: String = "No Help to Save account exists for the specified NINO"
  }
  case class ValidationError(message: String) extends ErrorInfo("VALIDATION_ERROR")

  implicit val writes: OWrites[ErrorInfo] = new OWrites[ErrorInfo] {

    override def writes(o: ErrorInfo): JsObject = o match {
      case General                  => obj("code" -> o.code)
      case AccountNotFound          => obj("code" -> o.code)
      case ValidationError(message) => obj("code" -> o.code, "message" -> message)
    }
  }
}
