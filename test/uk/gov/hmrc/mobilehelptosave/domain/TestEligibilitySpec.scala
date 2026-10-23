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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino

import java.time.Instant
class TestEligibilitySpec extends AnyWordSpec with Matchers {

  val nino = Nino("AB123456C")
  val expireAt = Instant.parse("2027-12-31T23:59:59Z")

  val eligibility =
    Eligibility(
      nino     = nino,
      eligible = true,
      expireAt = expireAt
    )

  "TestEligibility format" should {

    "serialize TestEligibility to JSON" in {
      val eligibility = TestEligibility(
        nino     = nino,
        eligible = true,
        isHashed = false
      )

      val json = Json.toJson(eligibility)

      (json \ "eligible").as[Boolean] mustBe true
      (json \ "isHashed").as[Boolean] mustBe false
      (json \ "nino").as[Nino] mustBe nino
    }

    "deserialize valid JSON to TestEligibility" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> true,
        "isHashed" -> false
      )

      val result = json.as[TestEligibility]

      result mustBe TestEligibility(
        nino     = nino,
        eligible = true,
        isHashed = false
      )
    }

    "round-trip a TestEligibility instance through JSON" in {
      val eligibility = TestEligibility(
        nino     = nino,
        eligible = false,
        isHashed = true
      )

      Json.fromJson[TestEligibility](Json.toJson(eligibility)) mustBe
        JsSuccess(eligibility)
    }

    "fail to deserialize JSON when a required field is missing" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> true
      )

      Json.fromJson[TestEligibility](json) mustBe a[JsError]
    }

    "fail to deserialize JSON when a field has the wrong type" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> "true",
        "isHashed" -> false
      )

      Json.fromJson[TestEligibility](json) mustBe a[JsError]
    }
  }

  "Eligibility JSON format" should {

    "serialize an Eligibility instance to JSON" in {
      val json = Json.toJson(eligibility)

      (json \ "nino").as[JsValue]     mustBe Json.toJson(nino)
      (json \ "eligible").as[Boolean] mustBe true

    }

    "deserialize valid JSON into an Eligibility instance" in {
      val json = Json.toJson(eligibility)

      json.as[Eligibility] mustBe eligibility
    }

    "preserve all fields when serializing and deserializing" in {
      val json = Json.toJson(eligibility)
      val result = Json.fromJson[Eligibility](json)

      result mustBe JsSuccess(eligibility)
    }

    "serialize eligible as false" in {
      val ineligible = eligibility.copy(eligible = false)

      val json = Json.toJson(ineligible)

      (json \ "eligible").as[Boolean] mustBe false
      json.as[Eligibility]            mustBe ineligible
    }

    "preserve the expiration instant" in {
      val json = Json.toJson(eligibility)
      val result = json.as[Eligibility]

      result.expireAt mustBe expireAt
    }

    "fail to deserialize JSON when eligible has the wrong type" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> "true",
        "expireAt" -> Json.toJson(expireAt)
      )

      Json.fromJson[Eligibility](json) mustBe a[JsError]
    }

    "fail to deserialize JSON when a required field is missing" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> true
      )

      Json.fromJson[Eligibility](json) mustBe a[JsError]
    }

    "fail to deserialize JSON when expireAt has an invalid format" in {
      val json = Json.obj(
        "nino"     -> Json.toJson(nino),
        "eligible" -> true,
        "expireAt" -> "not-a-valid-instant"
      )

      Json.fromJson[Eligibility](json) mustBe a[JsError]
    }
  }

}
