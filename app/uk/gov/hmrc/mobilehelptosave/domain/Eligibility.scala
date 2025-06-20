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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class EligibilityCheckResult(result: String, resultCode: Int, reason: String, reasonCode: Int)

object EligibilityCheckResult {

  implicit val format: Format[EligibilityCheckResult] = Json.format[EligibilityCheckResult]

}

case class EligibilityCheckResponse(eligibilityCheckResult: EligibilityCheckResult, threshold: Option[Double])

object EligibilityCheckResponse {

  implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]

}

case class Eligibility(nino: Nino, eligible: Boolean, expireAt: Instant)

object Eligibility {

  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit val format: Format[Eligibility] = Json.format[Eligibility]

}
