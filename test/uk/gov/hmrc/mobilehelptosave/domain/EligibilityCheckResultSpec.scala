/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
class EligibilityCheckResultSpec extends AnyWordSpec with Matchers {
  "EligibilityCheckResult" should {

    val result = EligibilityCheckResult(
      result = "Eligible",
      resultCode = 1,
      reason = "Meets criteria",
      reasonCode = 100
    )

    "serialize to JSON" in {
      val json = Json.toJson(result)

      (json \ "result").as[String] shouldBe "Eligible"
      (json \ "resultCode").as[Int] shouldBe 1
      (json \ "reason").as[String] shouldBe "Meets criteria"
      (json \ "reasonCode").as[Int] shouldBe 100
    }

    "deserialize from JSON" in {
      val json = Json.obj(
        "result" -> "Eligible",
        "resultCode" -> 1,
        "reason" -> "Meets criteria",
        "reasonCode" -> 100
      )

      json.as[EligibilityCheckResult] shouldBe result
    }

    "round-trip serialize and deserialize" in {
      Json.fromJson[EligibilityCheckResult](
        Json.toJson(result)
      ) shouldBe JsSuccess(result)
    }

    "fail when a required field is missing" in {
      val json = Json.obj(
        "result" -> "Eligible",
        "resultCode" -> 1,
        "reason" -> "Meets criteria"
      )

      Json.fromJson[EligibilityCheckResult](json) shouldBe
        a[JsError]
    }
  }
}
