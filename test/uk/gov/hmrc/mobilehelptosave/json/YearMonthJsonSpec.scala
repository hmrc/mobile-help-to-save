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

package uk.gov.hmrc.mobilehelptosave.json

import java.time.YearMonth
import play.api.libs.json._
import uk.gov.hmrc.mobilehelptosave.json.Formats.YearMonthFormat
import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

class YearMonthJsonSpec extends BaseSpec {
  "YearMonth JSON writes" should {
    "write in format YYYY-MM" in {
      Json.toJson(YearMonth.of(1999, 12)) shouldBe JsString("1999-12")
    }
  }

  "YearMonth JSON reads" should {
    "read from YYYY-MM string" in {
      JsString("1999-12").validate[YearMonth] shouldBe JsSuccess(YearMonth.of(1999, 12))
    }

    "reject non-String values with an error.expected.yearmonth error" in {
      JsNumber(1999).validate[YearMonth] shouldBe JsError("error.expected.yearmonth")
    }

    "reject badly formatted strings with an error.expected.yearmonth.format error" in {
      JsString("not a yearmonth").validate[YearMonth] shouldBe JsError(
        JsonValidationError("error.expected.yearmonth.format")
      )
    }
  }
}
