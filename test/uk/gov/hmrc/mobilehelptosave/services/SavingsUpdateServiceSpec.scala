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
import uk.gov.hmrc.mobilehelptosave.{AccountTestData, SavingsGoalTestData, TransactionTestData}
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
    with TransactionTestData
    with SavingsGoalTestData {

  val service = new HtsSavingsUpdateService()

  "getSavingsUpdateResponse" should {
    "do not return savings update section if no transactions found for reporting period" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(savingsUpdateMobileHelpToSaveAccount,
                                         transactionsSortedInMobileHelpToSaveOrder,
                                         dateDynamicSavingsGoalData)
      savingsUpdate.savingsUpdate.isEmpty shouldBe true
    }

    "calculate amount saved in reporting period correctly in savings update" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.savedInPeriod) shouldBe Some(BigDecimal(137.61))
    }

    "calculate months saved in reporting period correctly in savings update" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                           shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.savedByMonth).isDefined   shouldBe true
      savingsUpdate.savingsUpdate.get.savedByMonth.get.monthsSaved    shouldBe 4
      savingsUpdate.savingsUpdate.get.savedByMonth.get.numberOfMonths shouldBe 6
    }

    "not return goalsReached if user does not have a goal set currently" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
                                                    openedYearMonth  = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                       shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isEmpty shouldBe true
    }

    "calculate the number of times the goal has been hit against the current goal if no changes have been made during the reporting period" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                    currentBonusTerm = CurrentBonusTerm.Second,
                                                    savingsGoal      = Some(SavingsGoal(Some(10.0)))),
          transactionsDateDynamic,
          noChangeInPeriodSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 10.0
      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
    }

    "calculate the number of times a goal has been hit if a goal change has been made during the reporting period" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                    currentBonusTerm = CurrentBonusTerm.Second,
                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
          transactionsDateDynamic,
          singleChangeSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
    }

    "calculate the number of times a goal has been hit if several goal changes have been made during the reporting period" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                    currentBonusTerm = CurrentBonusTerm.Second,
                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
          transactionsDateDynamic,
          multipleChangeSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 2
    }

    "calculate the number of times a goal has been hit using the lowest goal amount if changed multiple times in a month" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                    currentBonusTerm = CurrentBonusTerm.Second,
                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
          transactionsDateDynamic,
          multipleChangeInMonthSavingsGoalData
        )
      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
    }

    "return current bonus estimate correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(90.99))
    }

    "return current bonus estimate correctly for user in second term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
                                                    openedYearMonth  = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(12))
    }

    "calculate highest balance correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(181.98))
    }

    "calculate highest balance correctly for user in final term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
                                                    openedYearMonth  = YearMonth.now().minusMonths(6)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(205.98))
    }

    "calculate potential bonus correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(28)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(394.59))
    }

    "calculate potential bonus correctly for user in second term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                 currentBonusTerm = CurrentBonusTerm.Second),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(80.53))
    }

    "don't return potential bonus if average savings amount is 0" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth  = YearMonth.now(),
                                                 currentBonusTerm = CurrentBonusTerm.Second),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe None
    }

    "calculate potential bonus with 5 increase correctly for user in first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(28)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore shouldBe Some(BigDecimal(449.59))
    }

    "calculate potential bonus with 5 increase correctly for user in second term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                 currentBonusTerm = CurrentBonusTerm.Second),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore shouldBe Some(BigDecimal(95.56))
    }

    "don't return potential bonus with 5 increase if average savings amount is > 45" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
          transactionsWithAverageSavingsRate(BigDecimal(45.01)),
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(542.90))
      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore  shouldBe None
    }

    "Calculate maximum bonus possible correctly for user in the first term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(28)),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.maxBonus shouldBe Some(BigDecimal(597.79))
    }

    "Calculate maximum bonus possible correctly for user in the second term" in {
      val savingsUpdate =
        service.getSavingsUpdateResponse(
          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(6),
                                                    currentBonusTerm = CurrentBonusTerm.Second),
          transactionsDateDynamic,
          dateDynamicSavingsGoalData
        )
      savingsUpdate.bonusUpdate.maxBonus shouldBe Some(BigDecimal(206.79))
    }

  }
}
