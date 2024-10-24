/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.LoggerLike

import java.time.{LocalDate, YearMonth}
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveBonusTerm, HttpClientV2Helper}


class AccountSpec extends HttpClientV2Helper with AccountTestData {

  val logger = mock[LoggerLike]
  private val accountOpenedInJan2018 = helpToSaveAccount.copy(
    openedYearMonth = YearMonth.of(2018, 1),
    bonusTerms = Seq(
      HelpToSaveBonusTerm(
        bonusEstimate          = BigDecimal("90.99"),
        bonusPaid              = BigDecimal("90.99"),
        endDate                = LocalDate.of(2019, 12, 31),
        bonusPaidOnOrAfterDate = LocalDate.of(2020, 1, 1)
      ),
      HelpToSaveBonusTerm(
        bonusEstimate          = 12,
        bonusPaid              = 0,
        endDate                = LocalDate.of(2021, 12, 31),
        bonusPaidOnOrAfterDate = LocalDate.of(2022, 1, 1)
      )
    )
  )

  "apply" should {

    "include nextPaymentMonthStartDate when next month will start before the end of the the last bonus term" in {
      val penultimateMonthHelpToSaveAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2021, 11, 30)
      )

      val account = Account(penultimateMonthHelpToSaveAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.nextPaymentMonthStartDate mustBe Some(LocalDate.of(2021, 12, 1))
    }

    "omit nextPaymentMonthStartDate when payments will not be possible next month because it will be after the last bonus term" in {
      val lastMonthHelpToSaveAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2021, 12, 31)
      )

      val account = Account(lastMonthHelpToSaveAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.nextPaymentMonthStartDate mustBe None
    }

    "return currentBonusTerm = *first* when current month is first month of *first* term" in {
      val firstMonthOfFirstTermHtSAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2018, 1, 31)
      )

      val account = Account(firstMonthOfFirstTermHtSAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.currentBonusTerm mustBe CurrentBonusTerm.First
    }

    "return currentBonusTerm = *first* when current month is last month of *first* term" in {
      val firstMonthOfFirstTermHtSAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2019, 12, 31)
      )

      val account = Account(firstMonthOfFirstTermHtSAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.currentBonusTerm mustBe CurrentBonusTerm.First
    }

    "return currentBonusTerm = *second* when current month is first month of *second* term" in {
      val firstMonthOfFirstTermHtSAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2020, 1, 31)
      )

      val account = Account(firstMonthOfFirstTermHtSAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.currentBonusTerm mustBe CurrentBonusTerm.Second
    }

    "return currentBonusTerm = *second* when current month is last month of *second* term" in {
      val firstMonthOfFirstTermHtSAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2021, 12, 31)
      )

      val account = Account(firstMonthOfFirstTermHtSAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.currentBonusTerm mustBe CurrentBonusTerm.Second
    }

    "return currentBonusTerm = *afterFinalTerm* when current month is after end of second term" in {
      val firstMonthOfFirstTermHtSAccount = accountOpenedInJan2018.copy(
        thisMonthEndDate = LocalDate.of(2022, 1, 31)
      )

      val account = Account(firstMonthOfFirstTermHtSAccount,
                            inAppPaymentsEnabled = false,
                            savingsGoalsEnabled  = false,
                            logger,
                            now,
                            None)
      account.currentBonusTerm mustBe CurrentBonusTerm.AfterFinalTerm
    }

    // balanceMustBeMoreThanForBonus is always 0 for the first term, we only include it for consistency with the second term
    "return balanceMustBeMoreThanForBonus = 0 for the first bonus term" in {
      val account =
        Account(accountOpenedInJan2018, inAppPaymentsEnabled = false, savingsGoalsEnabled = false, logger, now, None)
      account.bonusTerms.head.balanceMustBeMoreThanForBonus mustBe 0
    }

    "calculate the second bonus term's balanceMustBeMoreThanForBonus from the first term's bonusEstimate" in {
      val account =
        Account(accountOpenedInJan2018, inAppPaymentsEnabled = false, savingsGoalsEnabled = false, logger, now, None)
      account.bonusTerms(1).balanceMustBeMoreThanForBonus mustBe BigDecimal("181.98")
    }

    "log a warning and truncate the bonusTerms list when the source data contains more than 2 bonus terms, rather than returning an incorrect balanceMustBeMoreThanForBonus in the third term" in {

      val accountWith3Terms = helpToSaveAccount.copy(
        openedYearMonth = YearMonth.of(2018, 1),
        bonusTerms = Seq(
          HelpToSaveBonusTerm(
            bonusEstimate          = BigDecimal("90.99"),
            bonusPaid              = BigDecimal("90.99"),
            endDate                = LocalDate.of(2019, 12, 31),
            bonusPaidOnOrAfterDate = LocalDate.of(2020, 1, 1)
          ),
          HelpToSaveBonusTerm(
            bonusEstimate          = 12,
            bonusPaid              = 0,
            endDate                = LocalDate.of(2021, 12, 31),
            bonusPaidOnOrAfterDate = LocalDate.of(2022, 1, 1)
          ),
          HelpToSaveBonusTerm(
            bonusEstimate          = 0,
            bonusPaid              = 0,
            endDate                = LocalDate.of(2023, 12, 31),
            bonusPaidOnOrAfterDate = LocalDate.of(2024, 1, 1)
          )
        )
      )

      val account =
        Account(accountWith3Terms, inAppPaymentsEnabled = false, savingsGoalsEnabled = false, logger, now, None)
      account.bonusTerms.size mustBe 2
      // check that the first 2 terms were retained
      account.bonusTerms.head.endDate mustBe LocalDate.of(2019, 12, 31)
      account.bonusTerms(1).endDate   mustBe LocalDate.of(2021, 12, 31)
    }
  }
}
