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
import java.time.Instant
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
class EligibilityRecordSpec extends AnyWordSpec with Matchers {

  private val nino = Nino("AB123456C")
  private val hashNino = "hashed-nino-value"
  private val expireAt = Instant.parse("2027-12-31T23:59:59Z")

  private val eligibilityRecord = EligibilityRecord(
    nino     = Some(nino),
    hashNino = Some(hashNino),
    eligible = true,
    expireAt = expireAt
  )

  "EligibilityRecord JSON format" should {

    "serialize an EligibilityRecord to JSON" in {
      val json = Json.toJson(eligibilityRecord)

      (json \ "nino").as[JsValue]     shouldBe Json.toJson(nino)
      (json \ "hashNino").as[String]  shouldBe hashNino
      (json \ "eligible").as[Boolean] shouldBe true

    }

    "deserialize valid JSON into an EligibilityRecord" in {
      val json = Json.toJson(eligibilityRecord)

      json.as[EligibilityRecord] shouldBe eligibilityRecord
    }

    "round-trip an EligibilityRecord without losing data" in {
      val json = Json.toJson(eligibilityRecord)

      Json.fromJson[EligibilityRecord](json) shouldBe JsSuccess(eligibilityRecord)
    }

    "support an absent nino" in {
      val record = eligibilityRecord.copy(nino = None)

      Json.fromJson[EligibilityRecord](Json.toJson(record)) shouldBe JsSuccess(record)
    }

    "support an absent hashNino" in {
      val record = eligibilityRecord.copy(hashNino = None)

      Json.fromJson[EligibilityRecord](Json.toJson(record)) shouldBe JsSuccess(record)
    }

    "support both optional fields being absent" in {
      val record = eligibilityRecord.copy(
        nino     = None,
        hashNino = None
      )

      Json.fromJson[EligibilityRecord](Json.toJson(record)) shouldBe JsSuccess(record)
    }

    "preserve eligible as false" in {
      val record = eligibilityRecord.copy(eligible = false)
      val json = Json.toJson(record)

      (json \ "eligible").as[Boolean] shouldBe false
      json.as[EligibilityRecord]      shouldBe record
    }

    "preserve the expiration instant" in {
      val json = Json.toJson(eligibilityRecord)
      val result = json.as[EligibilityRecord]

      result.expireAt shouldBe expireAt
    }

    "fail when eligible has the wrong JSON type" in {
      val json = Json.toJson(eligibilityRecord).as[JsObject] ++
        Json.obj("eligible" -> "true")

      Json.fromJson[EligibilityRecord](json) shouldBe a[JsError]
    }

    "fail when expireAt has an invalid value" in {
      val json = Json.toJson(eligibilityRecord).as[JsObject] ++
        Json.obj("expireAt" -> "not-a-valid-instant")

      Json.fromJson[EligibilityRecord](json) shouldBe a[JsError]
    }

    "fail when a required field is missing" in {
      val json = Json.toJson(eligibilityRecord).as[JsObject] -
        "expireAt"

      Json.fromJson[EligibilityRecord](json) shouldBe a[JsError]
    }
  }
}
