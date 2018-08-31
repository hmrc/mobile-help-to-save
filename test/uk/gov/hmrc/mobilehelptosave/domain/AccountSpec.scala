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

import org.joda.time.{LocalDate, YearMonth}
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.mobilehelptosave.AccountTestData

class AccountSpec extends WordSpec with Matchers
  with AccountTestData {

  "apply" should {
    "include nextPaymentMonthStartDate when next month will start before the end of the the last bonus term" in {
      val penultimateMonthHelpToSaveAccount = helpToSaveAccount.copy(
        openedYearMonth = new YearMonth(2018, 1),
        thisMonthEndDate = new LocalDate(2021, 11, 30),
        bonusTerms = Seq(
          BonusTerm(
            bonusEstimate = BigDecimal("90.99"),
            bonusPaid = BigDecimal("90.99"),
            endDate = new LocalDate(2019, 12, 31),
            bonusPaidOnOrAfterDate = new LocalDate(2020, 1, 1)
          ),
          BonusTerm(
            bonusEstimate = 12,
            bonusPaid = 0,
            endDate = new LocalDate(2021, 12, 31),
            bonusPaidOnOrAfterDate = new LocalDate(2022, 1, 1)
          )
        )
      )

      val account = Account(penultimateMonthHelpToSaveAccount)
      account.nextPaymentMonthStartDate shouldBe Some(new LocalDate(2021, 12, 1))
    }

    "omit nextPaymentMonthStartDate when payments will not be possible next month because it will be after the last bonus term" in {
      val lastMonthHelpToSaveAccount = helpToSaveAccount.copy(
        openedYearMonth = new YearMonth(2018, 1),
        thisMonthEndDate = new LocalDate(2021, 12, 31),
        bonusTerms = Seq(
          BonusTerm(
            bonusEstimate = BigDecimal("90.99"),
            bonusPaid = BigDecimal("90.99"),
            endDate = new LocalDate(2019, 12, 31),
            bonusPaidOnOrAfterDate = new LocalDate(2020, 1, 1)
          ),
          BonusTerm(
            bonusEstimate = 12,
            bonusPaid = 0,
            endDate = new LocalDate(2021, 12, 31),
            bonusPaidOnOrAfterDate = new LocalDate(2022, 1, 1)
          )
        )
      )

      val account = Account(lastMonthHelpToSaveAccount)
      account.nextPaymentMonthStartDate shouldBe None
    }
  }
}
