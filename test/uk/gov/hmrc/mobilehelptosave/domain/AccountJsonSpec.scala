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

package uk.gov.hmrc.mobilehelptosave.domain

import com.eclipsesource.schema.SchemaType
import org.joda.time.{LocalDate, YearMonth}
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.mobilehelptosave.io.Resources
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers

class AccountJsonSpec extends WordSpec with Matchers with SchemaMatchers {
  private val ramlAccountSchema = loadResourceJson(
    "/public/api/conf/1.0/schemas/account.json"
  ).as[SchemaType]


  private val testAccount = Account(
    openedYearMonth = new YearMonth(2018, 5),
    isClosed = false,
    Blocking(false),
    BigDecimal("543.12"),
    0, 0, 0,
    thisMonthEndDate = new LocalDate(2020, 12, 31),
    bonusTerms = Seq.empty)


  "Account JSON" when {
    "account is blocked" should {
      "be a valid instance of the schema used in the RAML" in {
        val blockedAccount = testAccount.copy(blocked = Blocking(true))
        Json.toJson(blockedAccount) should validateAgainstSchema(ramlAccountSchema)
      }
    }
  }

  private def loadResourceJson(resourceName: String): JsValue =
    Resources.withResource(resourceName, getClass)(Json.parse)

}
