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

package uk.gov.hmrc.mobilehelptosave.services

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, TransactionTestData}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}
import java.time.YearMonth

class SavingsUpdateServiceSpec
    extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with TestF
    with AccountTestData
    with TransactionTestData {

  val service = new HtsSavingsUpdateService()

  "getSavingsUpdateResponse" should {
    "calculate amount saved in reporting period correctly in savings update" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
                                         transactionsDateDynamic)
      savingsUpdate.savingsUpdate.isDefined            shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.savedInPeriod) shouldBe Some(BigDecimal(137.61))
    }

    "calculate months saved in reporting period correctly in savings update" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
                                         transactionsDateDynamic)
      savingsUpdate.savingsUpdate.isDefined          shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.monthsSaved) shouldBe Some(4)
    }

    "do not return savings update section if no transactions found for reporting period" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount, transactionsSortedInMobileHelpToSaveOrder)
      savingsUpdate.savingsUpdate.isEmpty shouldBe true
    }

    "return current bonus estimate correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
                                         transactionsDateDynamic)
      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(90.99))
    }

    "return current bonus estimate correctly for user in second term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second),
          transactionsDateDynamic)
      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(12))
    }

    "calculate highest balance correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount, transactionsDateDynamic)
      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(181.98))
    }

    "calculate highest balance correctly for user in final term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(mobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second),
                                         transactionsDateDynamic)
      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(205.98))
    }
  }
}
