/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{LocalDate, YearMonth}

import com.eclipsesource.schema.SchemaType
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
    number          = "2000000000001",
    openedYearMonth = YearMonth.of(2018, 5),
    isClosed        = false,
    Blocking(unspecified = false, payments = false),
    BigDecimal("543.12"),
    0,
    0,
    0,
    thisMonthEndDate          =  LocalDate.of(2020, 12, 31),
    nextPaymentMonthStartDate = Some(LocalDate.of(2021, 1, 1)),
    accountHolderName         = "Testfore Testsur",
    accountHolderEmail        = Some("testemail@example.com"),
    bonusTerms = Seq(
      BonusTerm(
        bonusEstimate                 = BigDecimal("200.12"),
        bonusPaid                     = BigDecimal("200.12"),
        endDate                       = LocalDate.of(2020, 4, 30),
        bonusPaidOnOrAfterDate        = LocalDate.of(2020, 5, 1),
        balanceMustBeMoreThanForBonus = 0
      ),
      BonusTerm(
        bonusEstimate                 = BigDecimal("71.44"),
        bonusPaid                     = 0,
        endDate                       = LocalDate.of(2022, 4, 30),
        bonusPaidOnOrAfterDate        = LocalDate.of(2022, 5, 1),
        balanceMustBeMoreThanForBonus = BigDecimal("400.24")
      )
    ),
    currentBonusTerm     = CurrentBonusTerm.First,
    inAppPaymentsEnabled = false,
    daysRemainingInMonth = 1
  )

  "Account JSON" when {
    "account is in happy path state" should {
      "be a valid instance of the schema used in the RAML" in {
        Json.toJson(testAccount) should validateAgainstSchema(strictRamlAccountSchema)
      }
    }

    "account is blocked" should {
      "be a valid instance of the schema used in the RAML" in {
        val blockedAccount = testAccount.copy(blocked = Blocking(unspecified = true, payments = false))
        Json.toJson(blockedAccount) should validateAgainstSchema(strictRamlAccountSchema)
      }
    }

    "account is closed" should {
      "be a valid instance of the schema used in the RAML" in {
        val closedAccount = testAccount.copy(
          isClosed       = true,
          closureDate    = Some(LocalDate.of(2020, 11, 5)),
          closingBalance = Some(BigDecimal("543.12")),
          balance        = 0,
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
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.First)) \ "currentBonusTerm")
        .as[String] shouldBe "First"
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.Second)) \ "currentBonusTerm")
        .as[String] shouldBe "Second"
      (Json.toJson(testAccount.copy(currentBonusTerm = CurrentBonusTerm.AfterFinalTerm)) \ "currentBonusTerm")
        .as[String] shouldBe "AfterFinalTerm"
    }
  }
}
