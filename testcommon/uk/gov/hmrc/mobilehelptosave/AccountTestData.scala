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

package uk.gov.hmrc.mobilehelptosave

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, YearMonth}

import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveBonusTerm}
import uk.gov.hmrc.mobilehelptosave.domain._

trait AccountTestData {

  protected def accountReturnedByHelpToSaveJsonString(
    accountBalance:     BigDecimal,
    firstTermBonusPaid: BigDecimal,
    openedYearMonth:    YearMonth
  ): String =
    s"""
       |{
       |  "openedYearMonth": "$openedYearMonth",
       |  "accountNumber": "1000000000001",
       |  "isClosed": false,
       |  "blocked": {
       |    "payments": false,
       |    "withdrawals": false,
       |    "bonuses": false
       |  },
       |  "balance": $accountBalance,
       |  "paidInThisMonth": 27.88,
       |  "canPayInThisMonth": 22.12,
       |  "maximumPaidInThisMonth": 50,
       |  "thisMonthEndDate": "${YearMonth.now().minusYears(3).getYear}-04-30",
       |  "accountHolderForename": "Testfore",
       |  "accountHolderSurname": "Testsur",
       |  "accountHolderEmail": "testemail@example.com",
       |  "bonusTerms": [
       |    {
       |      "bonusEstimate": 90.99,
       |      "bonusPaid": $firstTermBonusPaid,
       |      "endDate": "${YearMonth.now().minusYears(2).getYear}-12-31",
       |      "bonusPaidOnOrAfterDate": "${YearMonth.now().minusYears(1).getYear}-01-01"
       |    },
       |    {
       |      "bonusEstimate": 12,
       |      "bonusPaid": 0,
       |      "endDate": "${YearMonth.now().getYear}-12-31",
       |      "bonusPaidOnOrAfterDate": "${YearMonth.now().plusYears(1).getYear}-01-01"
       |    }
       |  ],
       |  "nbaAccountNumber": "123456789",
       |  "nbaPayee": "Mr Testfore Testur",
       |  "nbaRollNumber": "RN136912",
       |  "nbaSortCode": "12-34-56"
       |}
    """.stripMargin

  protected def accountReturnedByHelpToSaveJsonStringDateDynamic(accountBalance: BigDecimal): String =
    s"""
       |{
       |  "openedYearMonth": "${YearMonth.now().minusMonths(6)}",
       |  "accountNumber": "1000000000001",
       |  "isClosed": false,
       |  "blocked": {
       |    "payments": false,
       |    "withdrawals": false,
       |    "bonuses": false
       |  },
       |  "balance": $accountBalance,
       |  "paidInThisMonth": 27.88,
       |  "canPayInThisMonth": 22.12,
       |  "maximumPaidInThisMonth": 50,
       |  "thisMonthEndDate": "${LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())}",
       |  "accountHolderForename": "Testfore",
       |  "accountHolderSurname": "Testsur",
       |  "accountHolderEmail": "testemail@example.com",
       |  "bonusTerms": [
       |    {
       |      "bonusEstimate": 90.99,
       |      "bonusPaid": 0,
       |      "endDate": "${LocalDate
         .now()
         .plusMonths(18)
         .withDayOfMonth(LocalDate.now().plusMonths(18).lengthOfMonth())}",
       |      "bonusPaidOnOrAfterDate": "${LocalDate.now().plusMonths(19) withDayOfMonth (1)}"
       |    },
       |    {
       |      "bonusEstimate": 12,
       |      "bonusPaid": 0,
       |      "endDate": "${LocalDate
         .now()
         .plusMonths(42)
         .withDayOfMonth(LocalDate.now().plusMonths(42).lengthOfMonth())}",
       |      "bonusPaidOnOrAfterDate": "${LocalDate.now().plusMonths(43) withDayOfMonth (1)}"
       |    }
       |  ]
       |}
    """.stripMargin

  protected def accountReturnedByHelpToSaveJsonString(
    accountBalance:                     BigDecimal,
    firstPeriodBonusEstimate:           BigDecimal,
    firstPeriodBonusPaid:               BigDecimal,
    firstPeriodEndDate:                 LocalDate,
    firstPeriodBonusPaidOnOrAfterDate:  LocalDate,
    secondPeriodBonusEstimate:          BigDecimal,
    secondPeriodBonusPaid:              BigDecimal,
    secondPeriodEndDate:                LocalDate,
    secondPeriodBonusPaidOnOrAfterDate: LocalDate,
    isClosed:                           Boolean = false
  ): String =
    s"""
       |{
       |  "openedYearMonth": "2018-01",
       |  "accountNumber": "1000000000001",
       |  "isClosed": $isClosed,
       |  "blocked": {
       |    "payments": false,
       |    "withdrawals": false,
       |    "bonuses": false
       |  },
       |  "balance": $accountBalance,
       |  "paidInThisMonth": 27.88,
       |  "canPayInThisMonth": 22.12,
       |  "maximumPaidInThisMonth": 50,
       |  "thisMonthEndDate": "${LocalDate.now().`with`(TemporalAdjusters.lastDayOfMonth())}",
       |  "accountHolderForename": "Testfore",
       |  "accountHolderSurname": "Testsur",
       |  "accountHolderEmail": "testemail@example.com",
       |  "bonusTerms": [
       |    {
       |      "bonusEstimate": $firstPeriodBonusEstimate,
       |      "bonusPaid": $firstPeriodBonusPaid,
       |      "endDate": "$firstPeriodEndDate",
       |      "bonusPaidOnOrAfterDate": "$firstPeriodBonusPaidOnOrAfterDate"
       |    },
       |    { 
       |      "bonusEstimate": $secondPeriodBonusEstimate,
       |      "bonusPaid": $secondPeriodBonusPaid,
       |      "endDate": "$secondPeriodEndDate",
       |      "bonusPaidOnOrAfterDate": "$secondPeriodBonusPaidOnOrAfterDate"
       |    }
       |  ]
       |}
    """.stripMargin

  protected val accountWithNoEmailReturnedByHelpToSaveJsonString: String =
    """
      |{
      |  "openedYearMonth": "2018-01",
      |  "accountNumber": "1000000000001",
      |  "isClosed": false,
      |  "blocked": {
      |    "payments": false,
      |    "withdrawals": false,
      |    "bonuses": false
      |  },
      |  "balance": 123.45,
      |  "paidInThisMonth": 27.88,
      |  "canPayInThisMonth": 22.12,
      |  "maximumPaidInThisMonth": 50,
      |  "thisMonthEndDate": "2018-04-30",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "bonusTerms": [
      |    {
      |      "bonusEstimate": 90.99,
      |      "bonusPaid": 90.99,
      |      "endDate": "2019-12-31",
      |      "bonusPaidOnOrAfterDate": "2020-01-01"
      |    },
      |    {
      |      "bonusEstimate": 12,
      |      "bonusPaid": 0,
      |      "endDate": "2021-12-31",
      |      "bonusPaidOnOrAfterDate": "2022-01-01"
      |    }
      |  ]
      |}
    """.stripMargin

  val monthEndDate: LocalDate = LocalDate.of(YearMonth.now().minusYears(3).getYear, 4, 30)
  val now:          LocalDate = LocalDate.of(YearMonth.now().minusYears(3).getYear, 4, 30)

  /** A HelpToSaveAccount object containing the same data as [[accountReturnedByHelpToSaveJsonString]] */
  protected val helpToSaveAccount: HelpToSaveAccount = HelpToSaveAccount(
    accountNumber          = "1000000000001",
    openedYearMonth        = YearMonth.of(YearMonth.now().minusYears(3).getYear, 1),
    isClosed               = false,
    blocked                = HtsBlocking(payments = false, withdrawals = false, bonuses = false),
    balance                = BigDecimal("123.45"),
    paidInThisMonth        = BigDecimal("27.88"),
    canPayInThisMonth      = BigDecimal("22.12"),
    maximumPaidInThisMonth = 50,
    thisMonthEndDate       = monthEndDate,
    accountHolderForename  = "Testfore",
    accountHolderSurname   = "Testsur",
    accountHolderEmail     = Some("testemail@example.com"),
    bonusTerms = Seq(
      HelpToSaveBonusTerm(
        bonusEstimate          = BigDecimal("90.99"),
        bonusPaid              = BigDecimal("90.99"),
        endDate                = LocalDate.of(YearMonth.now().minusYears(2).getYear, 12, 31),
        bonusPaidOnOrAfterDate = LocalDate.of(YearMonth.now().minusYears(1).getYear, 1, 1)
      ),
      HelpToSaveBonusTerm(
        bonusEstimate          = 12,
        bonusPaid              = 0,
        endDate                = LocalDate.of(YearMonth.now().getYear, 12, 31),
        bonusPaidOnOrAfterDate = LocalDate.of(YearMonth.now().plusYears(1).getYear, 1, 1)
      )
    ),
    closureDate      = None,
    closingBalance   = None,
    nbaAccountNumber = Some("123456789"),
    nbaPayee         = Some("Mr Testfore Testur"),
    nbaRollNumber    = Some("RN136912"),
    nbaSortCode      = Some("12-34-56")
  )

  /** An Account object containing the same data as [[accountReturnedByHelpToSaveJsonString]] */
  protected val mobileHelpToSaveAccount: Account = Account(
    number                    = "1000000000001",
    openedYearMonth           = YearMonth.of(YearMonth.now().minusYears(3).getYear, 1),
    isClosed                  = false,
    blocked                   = Blocking(payments = false, withdrawals = false, bonuses = false),
    balance                   = BigDecimal("123.45"),
    paidInThisMonth           = BigDecimal("27.88"),
    canPayInThisMonth         = BigDecimal("22.12"),
    maximumPaidInThisMonth    = 50,
    thisMonthEndDate          = monthEndDate,
    nextPaymentMonthStartDate = Some(LocalDate.of(YearMonth.now().minusYears(3).getYear, 5, 1)),
    accountHolderName         = "Testfore Testsur",
    accountHolderEmail        = Some("testemail@example.com"),
    bonusTerms = Seq(
      BonusTerm(
        bonusEstimate                 = BigDecimal("90.99"),
        bonusPaid                     = BigDecimal("90.99"),
        endDate                       = LocalDate.of(YearMonth.now().minusYears(2).getYear, 12, 31),
        bonusPaidOnOrAfterDate        = LocalDate.of(YearMonth.now().minusYears(1).getYear, 1, 1),
        bonusPaidByDate               = LocalDate.of(YearMonth.now().minusYears(1).getYear, 1, 1),
        balanceMustBeMoreThanForBonus = 0
      ),
      BonusTerm(
        bonusEstimate                 = 12,
        bonusPaid                     = 0,
        endDate                       = LocalDate.of(YearMonth.now().getYear, 12, 31),
        bonusPaidOnOrAfterDate        = LocalDate.of(YearMonth.now().plusYears(1).getYear, 1, 1),
        bonusPaidByDate               = LocalDate.of(YearMonth.now().plusYears(1).getYear, 1, 1),
        balanceMustBeMoreThanForBonus = BigDecimal("181.98")
      )
    ),
    currentBonusTerm     = CurrentBonusTerm.First,
    closureDate          = None,
    closingBalance       = None,
    nbaAccountNumber     = Some("123456789"),
    nbaPayee             = Some("Mr Testfore Testur"),
    nbaRollNumber        = Some("RN136912"),
    nbaSortCode          = Some("12-34-56"),
    inAppPaymentsEnabled = false,
    savingsGoalsEnabled  = true,
    savingsGoal          = None,
    1,
    highestBalance = 181.98,
    potentialBonus = Some(BigDecimal("90.99"))
  )

  protected val savingsUpdateMobileHelpToSaveAccount: Account = Account(
    number                    = "1000000000001",
    openedYearMonth           = YearMonth.of(YearMonth.now().minusYears(1).getYear, 1),
    isClosed                  = false,
    blocked                   = Blocking(payments = false, withdrawals = false, bonuses = false),
    balance                   = BigDecimal("123.45"),
    paidInThisMonth           = BigDecimal("27.88"),
    canPayInThisMonth         = BigDecimal("22.12"),
    maximumPaidInThisMonth    = 50,
    thisMonthEndDate          = monthEndDate,
    nextPaymentMonthStartDate = Some(LocalDate.of(YearMonth.now().minusYears(3).getYear, 5, 1)),
    accountHolderName         = "Testfore Testsur",
    accountHolderEmail        = Some("testemail@example.com"),
    bonusTerms = Seq(
      BonusTerm(
        bonusEstimate = BigDecimal("90.99"),
        bonusPaid     = BigDecimal("90.99"),
        endDate = LocalDate.of(YearMonth.now().plusMonths(18).getYear,
                               YearMonth.now().plusMonths(18).getMonth,
                               YearMonth.now().plusMonths(18).lengthOfMonth()),
        bonusPaidOnOrAfterDate =
          LocalDate.of(YearMonth.now().plusMonths(19).getYear, YearMonth.now().plusMonths(19).getMonth, 1),
        bonusPaidByDate =
          LocalDate.of(YearMonth.now().plusMonths(19).getYear, YearMonth.now().plusMonths(19).getMonth, 1),
        balanceMustBeMoreThanForBonus = 0
      ),
      BonusTerm(
        bonusEstimate = 12,
        bonusPaid     = 0,
        endDate = LocalDate.of(YearMonth.now().plusMonths(42).getYear,
                               YearMonth.now().plusMonths(42).getMonth,
                               YearMonth.now().plusMonths(42).lengthOfMonth()),
        bonusPaidOnOrAfterDate =
          LocalDate.of(YearMonth.now().plusMonths(43).getYear, YearMonth.now().plusMonths(43).getMonth, 1),
        bonusPaidByDate =
          LocalDate.of(YearMonth.now().plusMonths(43).getYear, YearMonth.now().plusMonths(43).getMonth, 1),
        balanceMustBeMoreThanForBonus = BigDecimal("300")
      )
    ),
    currentBonusTerm     = CurrentBonusTerm.First,
    closureDate          = None,
    closingBalance       = None,
    nbaAccountNumber     = Some("123456789"),
    nbaPayee             = Some("Mr Testfore Testur"),
    nbaRollNumber        = Some("RN136912"),
    nbaSortCode          = Some("12-34-56"),
    inAppPaymentsEnabled = false,
    savingsGoalsEnabled  = true,
    savingsGoal          = None,
    1,
    highestBalance = 300
  )

  protected val mobileHelpToSaveAccountSecondTerm: Account = Account(
    number                    = "1000000000001",
    openedYearMonth           = YearMonth.of(YearMonth.now().minusYears(3).getYear, 1),
    isClosed                  = false,
    blocked                   = Blocking(payments = false, withdrawals = false, bonuses = false),
    balance                   = BigDecimal("123.45"),
    paidInThisMonth           = BigDecimal("27.88"),
    canPayInThisMonth         = BigDecimal("22.12"),
    maximumPaidInThisMonth    = 50,
    thisMonthEndDate          = monthEndDate,
    nextPaymentMonthStartDate = Some(LocalDate.of(YearMonth.now().minusYears(3).getYear, 5, 1)),
    accountHolderName         = "Testfore Testsur",
    accountHolderEmail        = Some("testemail@example.com"),
    bonusTerms = Seq(
      BonusTerm(
        bonusEstimate                 = BigDecimal("90.99"),
        bonusPaid                     = BigDecimal("90.99"),
        endDate                       = LocalDate.of(YearMonth.now().minusYears(2).getYear, 12, 31),
        bonusPaidOnOrAfterDate        = LocalDate.of(YearMonth.now().minusYears(2).getYear, 1, 1),
        bonusPaidByDate               = LocalDate.of(YearMonth.now().minusYears(2).getYear, 1, 1),
        balanceMustBeMoreThanForBonus = 0
      ),
      BonusTerm(
        bonusEstimate = 12,
        bonusPaid     = 0,
        endDate = LocalDate.of(YearMonth.now().plusMonths(6).getYear,
                               YearMonth.now().plusMonths(6).getMonth,
                               YearMonth.now().plusMonths(6).lengthOfMonth()),
        bonusPaidOnOrAfterDate =
          LocalDate.of(YearMonth.now().plusMonths(7).getYear, YearMonth.now().plusMonths(7).getMonth, 1),
        bonusPaidByDate =
          LocalDate.of(YearMonth.now().plusMonths(7).getYear, YearMonth.now().plusMonths(7).getMonth, 1),
        balanceMustBeMoreThanForBonus = BigDecimal("100.00")
      )
    ),
    currentBonusTerm     = CurrentBonusTerm.Second,
    closureDate          = None,
    closingBalance       = None,
    nbaAccountNumber     = Some("123456789"),
    nbaPayee             = Some("Mr Testfore Testur"),
    nbaRollNumber        = Some("RN136912"),
    nbaSortCode          = Some("12-34-56"),
    inAppPaymentsEnabled = false,
    savingsGoalsEnabled  = true,
    savingsGoal          = None,
    1,
    highestBalance = 100
  )

  protected val closedAccountReturnedByHelpToSaveJsonString: String =
    """
      |{
      |  "openedYearMonth": "2018-03",
      |  "accountNumber": "1000000000002",
      |  "isClosed": true,
      |  "blocked": {
      |    "payments": false,
      |    "withdrawals": false,
      |    "bonuses": false
      |  },
      |  "balance": 0,
      |  "paidInThisMonth": 0,
      |  "canPayInThisMonth": 50,
      |  "maximumPaidInThisMonth": 50,
      |  "thisMonthEndDate": "2018-04-30",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "accountHolderEmail": "testemail@example.com",
      |  "bonusTerms": [
      |    {
      |      "bonusEstimate": 7.50,
      |      "bonusPaid": 0,
      |      "endDate": "2020-02-29",
      |      "bonusPaidOnOrAfterDate": "2020-03-01"
      |    },
      |    {
      |      "bonusEstimate": 0,
      |      "bonusPaid": 0,
      |      "endDate": "2022-02-28",
      |      "bonusPaidOnOrAfterDate": "2022-03-01"
      |    }
      |  ],
      |  "closureDate": "2018-04-09",
      |  "closingBalance": 10
      |}
    """.stripMargin

  protected val enrolledButPaymentsBlockedReturnedByHelpToSaveJsonString: String =
    """{
      |  "accountNumber" : "1100000112068",
      |  "openedYearMonth" : "2017-11",
      |  "isClosed" : false,
      |  "blocked" : {
      |    "payments": true,
      |    "withdrawals": false,
      |    "bonuses": false
      |  },
      |  "balance" : 250,
      |  "paidInThisMonth" : 50,
      |  "canPayInThisMonth" : 0,
      |  "maximumPaidInThisMonth" : 50,
      |  "thisMonthEndDate" : "2018-03-31",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "accountHolderEmail": "testemail@example.com",
      |  "bonusTerms" : [ {
      |    "bonusEstimate" : 125,
      |    "bonusPaid" : 0,
      |    "endDate" : "2019-10-31",
      |    "bonusPaidOnOrAfterDate" : "2019-11-01"
      |  }, {
      |    "bonusEstimate" : 0,
      |    "bonusPaid" : 0,
      |    "endDate" : "2021-10-31",
      |    "bonusPaidOnOrAfterDate" : "2021-11-01"
      |  } ]
      |}""".stripMargin

  protected val enrolledButWithdrawalsBlockedReturnedByHelpToSaveJsonString: String =
    """{
      |  "accountNumber" : "1100000112068",
      |  "openedYearMonth" : "2017-11",
      |  "isClosed" : false,
      |  "blocked" : {
      |    "payments": false,
      |    "withdrawals": true,
      |    "bonuses": false
      |  },
      |  "balance" : 250,
      |  "paidInThisMonth" : 50,
      |  "canPayInThisMonth" : 0,
      |  "maximumPaidInThisMonth" : 50,
      |  "thisMonthEndDate" : "2018-03-31",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "accountHolderEmail": "testemail@example.com",
      |  "bonusTerms" : [ {
      |    "bonusEstimate" : 125,
      |    "bonusPaid" : 0,
      |    "endDate" : "2019-10-31",
      |    "bonusPaidOnOrAfterDate" : "2019-11-01"
      |  }, {
      |    "bonusEstimate" : 0,
      |    "bonusPaid" : 0,
      |    "endDate" : "2021-10-31",
      |    "bonusPaidOnOrAfterDate" : "2021-11-01"
      |  } ]
      |}""".stripMargin

  protected val enrolledButBonusesBlockedReturnedByHelpToSaveJsonString: String =
    """{
      |  "accountNumber" : "1100000112068",
      |  "openedYearMonth" : "2017-11",
      |  "isClosed" : false,
      |  "blocked" : {
      |    "payments": false,
      |    "withdrawals": false,
      |    "bonuses": true
      |  },
      |  "balance" : 250,
      |  "paidInThisMonth" : 50,
      |  "canPayInThisMonth" : 0,
      |  "maximumPaidInThisMonth" : 50,
      |  "thisMonthEndDate" : "2018-03-31",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "accountHolderEmail": "testemail@example.com",
      |  "bonusTerms" : [ {
      |    "bonusEstimate" : 125,
      |    "bonusPaid" : 0,
      |    "endDate" : "2019-10-31",
      |    "bonusPaidOnOrAfterDate" : "2019-11-01"
      |  }, {
      |    "bonusEstimate" : 0,
      |    "bonusPaid" : 0,
      |    "endDate" : "2021-10-31",
      |    "bonusPaidOnOrAfterDate" : "2021-11-01"
      |  } ]
      |}""".stripMargin

  // invalid because required field isClosed is omitted
  protected val accountReturnedByHelpToSaveInvalidJsonString: String =
    """
      |{
      |  "openedYearMonth": "2017-11",
      |  "accountNumber": "1000000000001",
      |  "blocked": {
      |    "payments": false,
      |    "withdrawals": false,
      |    "bonuses": false
      |  },
      |  "balance": 249.45,
      |  "paidInThisMonth": 27.88,
      |  "canPayInThisMonth": 22.12,
      |  "maximumPaidInThisMonth": 50,
      |  "thisMonthEndDate": "2018-04-30",
      |  "accountHolderForename": "Testfore",
      |  "accountHolderSurname": "Testsur",
      |  "accountHolderEmail": "testemail@example.com",
      |  "bonusTerms": [
      |    {
      |      "bonusEstimate": 125,
      |      "bonusPaid": 0,
      |      "endDate": "2019-10-31",
      |      "bonusPaidOnOrAfterDate": "2019-11-01"
      |    },
      |    {
      |      "bonusEstimate": 0,
      |      "bonusPaid": 0,
      |      "endDate": "2021-10-31",
      |      "bonusPaidOnOrAfterDate": "2021-11-01"
      |    }
      |  ]
      |}
    """.stripMargin

  protected val mobileHelpToSaveAccountNoNbaDetails: Account = mobileHelpToSaveAccount.copy(nbaAccountNumber = None, nbaPayee = None, nbaRollNumber = None, nbaSortCode = None)
}
