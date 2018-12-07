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

package uk.gov.hmrc.mobilehelptosave.repository

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpecLike, Matchers}
import play.api.libs.json.{JsError, JsString, JsSuccess, Json}
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEventType._

class SavingsGoalEventTypeTest extends FreeSpecLike with Matchers with TableDrivenPropertyChecks {

  "json values for event types should be lower case" in {
    val table = Table(
      ("event", "jsvalue"),
      (Delete, "delete"),
      (Set, "set")
    )

    forAll(table) { (event: SavingsGoalEventType, s: String) =>
      Json.toJson(event) shouldBe JsString(s)
    }
  }

  "check round-trip to json value" in {
    SavingsGoalEventType.values.foreach { event =>
      Json.fromJson(Json.toJson(event)) match {
        case JsSuccess(e, _) => e shouldBe event
        case JsError(errs)   => fail(s"Round-trip failed for $event with errors $errs")
      }
    }
  }

}
