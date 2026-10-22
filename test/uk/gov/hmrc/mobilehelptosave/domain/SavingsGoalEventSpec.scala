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
import uk.gov.hmrc.mobilehelptosave.repository.*

import java.time.Instant

class SavingsGoalEventSpec extends AnyWordSpec with Matchers {
  private val nino = Nino("AA123456A")
  private val date: Instant = Instant.parse("2026-01-15T10:30:00Z")
  private val expireAt = Instant.parse("2030-07-15T10:30:00Z")
  "SavingsGoalRepoModel" should {

    "serialize to JSON" in {
      val model = SavingsGoalRepoModel(nino, 1500.50, date)

      val json = Json.toJson(model)(SavingsGoalRepoModel.format)

      (json \ "nino").as[String] mustBe nino.value
      (json \ "amount").as[Double] mustBe 1500.50
      (json \ "createdAt").as[Instant] mustBe date
    }

    "deserialize from valid JSON" in {
      val json = Json.obj(
        "nino"      -> nino.value,
        "amount"    -> 1500.50,
        "createdAt" -> Json.toJson(date)
      )

      val result = Json.fromJson[SavingsGoalRepoModel](json)

      result mustBe JsSuccess(
        SavingsGoalRepoModel(nino, 1500.50, date)
      )
    }

    "reject JSON with a missing required field" in {
      val json = Json.obj(
        "nino"   -> nino.value,
        "amount" -> 1500.50
      )

      Json.fromJson[SavingsGoalRepoModel](json) mustBe a[JsError]
    }

    "reject JSON with an invalid amount" in {
      val json = Json.obj(
        "nino"      -> nino.value,
        "amount"    -> "not-a-number",
        "createdAt" -> Json.toJson(date)(SavingsGoalEvent.dateFormat)
      )

      Json.fromJson[SavingsGoalRepoModel](json) mustBe a[JsError]
    }

  }

  "SavingsGoalEventType" should {
    "deserialize the delete type" in {
      Json.fromJson[SavingsGoalEventType](JsString("delete")) mustBe
        JsSuccess(SavingsGoalEventType.Delete)
    }

    "deserialize the set type" in {
      Json.fromJson[SavingsGoalEventType](JsString("set")) mustBe
        JsSuccess(SavingsGoalEventType.Set)
    }

    "serialize the delete type as lowercase" in {
      Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Delete) mustBe
        JsString("delete")
    }

    "serialize the set type as lowercase" in {
      Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Set) mustBe
        JsString("set")
    }

    "reject an unknown event type" in {
      SavingsGoalEventType.format.reads(JsString("update")) mustBe
        JsError("Invalid savings goal type")
    }
  }

  "SavingsGoalEvent" should {

    "serialize a set event with type set" in {
      val event = SavingsGoalSetEvent(
        nino           = nino,
        amount         = Some(1500.0),
        date           = Instant.now(),
        name           = Some("Holiday"),
        expireAt       = expireAt,
        updateRequired = true
      )

      val json = Json.toJson[SavingsGoalEvent](event)

      (json \ "type").as[String] mustBe "set"
      (json \ "nino").as[String] mustBe nino.value
      (json \ "amount").as[Double] mustBe 1500.0
      (json \ "name").as[String] mustBe "Holiday"
      (json \ "updateRequired").as[Boolean] mustBe true
    }

    "serialize a delete event with type delete" in {
      val event = SavingsGoalDeleteEvent(
        nino           = nino,
        date           = Instant.now(),
        expireAt       = expireAt,
        updateRequired = true
      )

      val json = Json.toJson[SavingsGoalEvent](event)

      (json \ "type").as[String] mustBe "delete"
      (json \ "nino").as[String] mustBe nino.value
      (json \ "updateRequired").as[Boolean] mustBe true
    }

    "deserialize a set event" in {
      val event = SavingsGoalSetEvent(
        nino           = nino,
        amount         = Some(1500.0),
        date           = date,
        name           = Some("Holiday"),
        expireAt       = expireAt,
        updateRequired = true
      )

      val json = Json.toJson[SavingsGoalEvent](event)

      Json.fromJson[SavingsGoalEvent](json) mustBe JsSuccess(event)
    }

    "deserialize a delete event" in {
      val event = SavingsGoalDeleteEvent(
        nino           = nino,
        date           = date,
        expireAt       = expireAt,
        updateRequired = true
      )

      val json = Json.toJson[SavingsGoalEvent](event)

      Json.fromJson[SavingsGoalEvent](json) mustBe JsSuccess(event)
    }

    "round-trip a set event through JSON" in {
      val event: SavingsGoalEvent = SavingsGoalSetEvent(
        nino           = nino,
        amount         = Some(2000.0),
        date           = date,
        name           = None,
        expireAt       = expireAt,
        updateRequired = false
      )

      Json.fromJson[SavingsGoalEvent](Json.toJson(event)) mustBe
        JsSuccess(event)
    }

    "round-trip a delete event through JSON" in {
      val event: SavingsGoalEvent = SavingsGoalDeleteEvent(
        nino           = nino,
        date           = date,
        expireAt       = expireAt,
        updateRequired = false
      )

      Json.fromJson[SavingsGoalEvent](Json.toJson(event)) mustBe
        JsSuccess(event)
    }

    "deserialize a set event with optional fields absent" in {
      val json = Json.obj(
        "type"           -> "set",
        "nino"           -> nino.value,
        "date"           -> Json.toJson(date)(SavingsGoalEvent.dateFormat),
        "expireAt"       -> Json.toJson(expireAt)(SavingsGoalEvent.dateFormat),
        "updateRequired" -> false
      )

      val result = Json.fromJson[SavingsGoalEvent](json)

      result mustBe JsSuccess(
        SavingsGoalSetEvent(
          nino           = nino,
          amount         = None,
          date           = date,
          name           = None,
          expireAt       = expireAt,
          updateRequired = false
        )
      )
    }

    "reject an event with an unknown type" in {
      val json = Json.obj(
        "type" -> "update",
        "nino" -> nino.value,
        "date" -> Json.toJson(date)(SavingsGoalEvent.dateFormat)
      )

      Json.fromJson[SavingsGoalEvent](json) mustBe a[JsError]
    }

    "reject an event when type is missing" in {
      val json = Json.obj(
        "nino" -> nino.value,
        "date" -> Json.toJson(date)(SavingsGoalEvent.dateFormat)
      )

      Json.fromJson[SavingsGoalEvent](json) mustBe a[JsError]
    }

    "reject a set event when a required field is missing" in {
      val json = Json.obj(
        "type"   -> "set",
        "nino"   -> nino.value,
        "amount" -> 1500.0
      )

      Json.fromJson[SavingsGoalEvent](json) mustBe a[JsError]
    }

    "reject a delete event when a required field is missing" in {
      val json = Json.obj(
        "type" -> "delete",
        "nino" -> nino.value
      )

      Json.fromJson[SavingsGoalEvent](json) mustBe a[JsError]
    }
  }

}
