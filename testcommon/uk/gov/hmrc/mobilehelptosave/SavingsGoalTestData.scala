/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalSetEvent

import java.time.LocalDateTime

trait SavingsGoalTestData {

  protected val dateDynamicSavingsGoalData = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(10), LocalDateTime.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(15), LocalDateTime.now().minusMonths(1)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(20), LocalDateTime.now().minusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), LocalDateTime.now().minusMonths(8)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(45), LocalDateTime.now().minusMonths(9))
  )

  protected val noChangeInPeriodSavingsGoalData = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), LocalDateTime.now().minusYears(1)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(45), LocalDateTime.now().minusMonths(19))
  )

  protected val singleChangeSavingsGoalData = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), LocalDateTime.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(20), LocalDateTime.now().minusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), LocalDateTime.now().minusMonths(8))
  )

  protected val multipleChangeSavingsGoalData = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), LocalDateTime.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(100), LocalDateTime.now().minusMonths(1)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), LocalDateTime.now().minusMonths(4)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(12.50), LocalDateTime.now().minusMonths(8))
  )

  protected val multipleChangeInMonthSavingsGoalData = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), LocalDateTime.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), LocalDateTime.now().minusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(30), LocalDateTime.now().minusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(40), LocalDateTime.now().minusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(25), LocalDateTime.now().minusMonths(8))
  )

  // invalid because required field isClosed is omitted
 // protected val accountReturnedByHelpToSaveInvalidJsonString: String =
    """
      |{
      |  "openedYearMonth": "2017-11",
      |  "accountNumber": "1000000000001",
      |  "blocked": {
      |    "unspecified": false,
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
}
