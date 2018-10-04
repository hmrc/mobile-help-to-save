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
import play.api.libs.json.Json
import uk.gov.hmrc.mobilehelptosave.json.JsonResource.loadResourceJson
import uk.gov.hmrc.mobilehelptosave.json.Schema.banAdditionalProperties
import uk.gov.hmrc.mobilehelptosave.scalatest.SchemaMatchers

class AccountJsonSpec extends WordSpec with Matchers with SchemaMatchers {

  private val strictRamlAccountSchema =
    banAdditionalProperties(loadResourceJson("/public/api/conf/1.0/schemas/account.json"))
      .as[SchemaType]

  private val testAccount = Account(
    number = "2000000000001",
    openedYearMonth = new YearMonth(2018, 5),
    isClosed = false,
    Blocking(false),
    BigDecimal("543.12"),
    0, 0, 0,
    thisMonthEndDate = new LocalDate(2020, 12, 31),
    nextPaymentMonthStartDate = Some(new LocalDate(2021, 1, 1)),
    accountHolderName = "Testfore Testsur",
    accountHolderEmail = Some("testemail@example.com"),
    bonusTerms = Seq(
      BonusTerm(bonusEstimate = BigDecimal("200.12"), bonusPaid = BigDecimal("200.12"), endDate = new LocalDate(2020, 4, 30), bonusPaidOnOrAfterDate = new LocalDate(2020, 5, 1)),
      BonusTerm(bonusEstimate = BigDecimal("71.44"), bonusPaid = 0, endDate = new LocalDate(2022, 4, 30), bonusPaidOnOrAfterDate = new LocalDate(2022, 5, 1))
    ),
    currentBonusTerm = CurrentBonusTerm.First
  )

  "Account JSON" when {
    "account is in happy path state" should {
      "be a valid instance of the schema used in the RAML" in {
        Json.toJson(testAccount) should validateAgainstSchema(strictRamlAccountSchema)
      }
    }

    "account is blocked" should {
      "be a valid instance of the schema used in the RAML" in {
        val blockedAccount = testAccount.copy(blocked = Blocking(true))
        Json.toJson(blockedAccount) should validateAgainstSchema(strictRamlAccountSchema)
      }
    }

    "account is closed" should {
      "be a valid instance of the schema used in the RAML" in {
        val closedAccount = testAccount.copy(
          isClosed = true,
          closureDate = Some(new LocalDate(2020, 11, 5)),
          closingBalance = Some(BigDecimal("543.12")),
          balance = 0,
          bonusTerms = Seq(
            testAccount.bonusTerms.head,
            testAccount.bonusTerms(1).copy(bonusEstimate = 0)
          )
        )
        Json.toJson(closedAccount) should validateAgainstSchema(strictRamlAccountSchema)
      }
    }
  }

  "Account JSON" should {
    "format currentBonusTerm as documented in the README" in {
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.First)) \ "currentBonusTerm").as[String] shouldBe "First"
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.Second)) \ "currentBonusTerm").as[String] shouldBe "Second"
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.AfterFinalTerm)) \ "currentBonusTerm").as[String] shouldBe "AfterFinalTerm"
    }
  }
}
