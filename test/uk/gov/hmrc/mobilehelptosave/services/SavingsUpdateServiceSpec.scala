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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.mobilehelptosave.services
//
//import uk.gov.hmrc.domain.Nino
//import uk.gov.hmrc.mobilehelptosave.{AccountTestData, SavingsGoalTestData, TransactionTestData}
//import uk.gov.hmrc.mobilehelptosave.domain._
//import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalSetEvent
//import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, TestF}
//
//import java.time.{Instant, LocalDate, YearMonth}
//
//class SavingsUpdateServiceSpec
//    extends BaseSpec
//    with TestF
//    with AccountTestData
//    with TransactionTestData
//    with SavingsGoalTestData {
//
//  val service = new HtsSavingsUpdateService()
//
//  "getSavingsUpdateResponse" should {
//    "do not return savings update section if no transactions found for reporting period" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(savingsUpdateMobileHelpToSaveAccount,
//                                         transactionsSortedInMobileHelpToSaveOrder,
//                                         dateDynamicSavingsGoalData)
//      savingsUpdate.savingsUpdate.isEmpty shouldBe true
//    }
//
//    "calculate amount saved in reporting period correctly in savings update" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.savedInPeriod) shouldBe Some(BigDecimal(137.61))
//    }
//
//    "calculate months saved in reporting period correctly in savings update" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                           shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.savedByMonth).isDefined   shouldBe true
//      savingsUpdate.savingsUpdate.get.savedByMonth.get.monthsSaved    shouldBe 4
//      savingsUpdate.savingsUpdate.get.savedByMonth.get.numberOfMonths shouldBe 6
//    }
//
//    "not return goalsReached if user does not have a goal set currently" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
//                                                    openedYearMonth  = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                       shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isEmpty shouldBe true
//    }
//
//    "not return goalsReached if user has set their first goal in the current month" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
//                                                    openedYearMonth  = YearMonth.now().minusMonths(30),
//                                                    savingsGoal      = Some(SavingsGoal(Some(10), Some("Holiday")))),
//          transactionsDateDynamic,
//          List(SavingsGoalSetEvent(Nino("CS700100A"), Some(10), Instant.now()))
//        )
//      savingsUpdate.savingsUpdate.isDefined                       shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isEmpty shouldBe true
//    }
//
//    "calculate the number of times the goal has been hit against the current goal if no changes have been made during the reporting period" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(30),
//                                                    currentBonusTerm = CurrentBonusTerm.Second,
//                                                    savingsGoal      = Some(SavingsGoal(Some(10.0)))),
//          transactionsDateDynamic,
//          noChangeInPeriodSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
//      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 10.0
//      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
//    }
//
//    "calculate the number of times a goal has been hit if a goal change has been made during the reporting period" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(30),
//                                                    currentBonusTerm = CurrentBonusTerm.Second,
//                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
//          transactionsDateDynamic,
//          singleChangeSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
//      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
//      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
//    }
//
//    "calculate the number of times a goal has been hit if several goal changes have been made during the reporting period" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(30),
//                                                    currentBonusTerm = CurrentBonusTerm.Second,
//                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
//          transactionsDateDynamic,
//          multipleChangeSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
//      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
//      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 2
//    }
//
//    "calculate the number of times a goal has been hit using the lowest goal amount if changed multiple times in a month" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth  = YearMonth.now().minusMonths(30),
//                                                    currentBonusTerm = CurrentBonusTerm.Second,
//                                                    savingsGoal      = Some(SavingsGoal(Some(50.0)))),
//          transactionsDateDynamic,
//          multipleChangeInMonthSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.isDefined                                 shouldBe true
//      savingsUpdate.savingsUpdate.flatMap(_.goalsReached).isDefined         shouldBe true
//      savingsUpdate.savingsUpdate.get.goalsReached.get.currentAmount        shouldBe 50.0
//      savingsUpdate.savingsUpdate.get.goalsReached.get.numberOfTimesReached shouldBe 3
//    }
//
//    "Calculate amount earned towards next bonus correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.flatMap(_.amountEarnedTowardsBonus) shouldBe Some(120.00)
//    }
//
//    "Calculate amount earned towards next bonus correctly for user in second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.savingsUpdate.flatMap(_.amountEarnedTowardsBonus) shouldBe Some(120.00)
//    }
//
//    "return months until next bonus correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.monthsUntilBonus shouldBe 19
//    }
//
//    "return months until next bonus correctly for user in second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.monthsUntilBonus shouldBe 7
//    }
//
//    "round up months correctly" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(
//            openedYearMonth = YearMonth.now().minusMonths(30),
//            bonusTerms = Seq(
//              BonusTerm(
//                bonusEstimate                 = BigDecimal("90.99"),
//                bonusPaid                     = BigDecimal("90.99"),
//                endDate                       = LocalDate.of(YearMonth.now().minusYears(2).getYear, 12, 31),
//                bonusPaidOnOrAfterDate        = LocalDate.of(YearMonth.now().minusYears(2).getYear, 1, 1),
//                bonusPaidByDate               = LocalDate.of(YearMonth.now().minusYears(2).getYear, 1, 1),
//                balanceMustBeMoreThanForBonus = 0
//              ),
//              BonusTerm(
//                bonusEstimate = 12,
//                bonusPaid     = 0,
//                endDate =
//                  LocalDate.of(YearMonth.now().getYear, YearMonth.now().getMonth, YearMonth.now().lengthOfMonth()),
//                bonusPaidOnOrAfterDate =
//                  LocalDate.of(YearMonth.now().plusMonths(7).getYear, YearMonth.now().plusMonths(7).getMonth, 1),
//                bonusPaidByDate =
//                  LocalDate.of(YearMonth.now().plusMonths(7).getYear, YearMonth.now().plusMonths(7).getMonth, 1),
//                balanceMustBeMoreThanForBonus = BigDecimal("100.00")
//              )
//            )
//          ),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.monthsUntilBonus shouldBe 1
//    }
//
//    "return current bonus estimate correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(90.99))
//    }
//
//    "return current bonus estimate correctly for user in second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
//                                                    openedYearMonth  = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.currentBonus shouldBe Some(BigDecimal(12))
//    }
//
//    "calculate highest balance correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(300.00))
//    }
//
//    "calculate highest balance correctly for user in final term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(currentBonusTerm = CurrentBonusTerm.Second,
//                                                    openedYearMonth  = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.highestBalance shouldBe Some(BigDecimal(300.00))
//    }
//
//    "calculate potential bonus correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(268.14))
//    }
//
//    "calculate potential bonus correctly for user in second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(80.53))
//    }
//
//    "don't return potential bonus if average savings amount is 0" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now()),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe None
//    }
//
//    "calculate potential bonus with 5 increase correctly for user in first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore shouldBe Some(BigDecimal(313.17))
//    }
//
//    "calculate potential bonus with 5 increase correctly for user in second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore shouldBe Some(BigDecimal(95.56))
//    }
//
//    "don't return potential bonus with 5 increase if average savings amount is > 45" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(6)),
//          transactionsWithAverageSavingsRate(BigDecimal(45.01)),
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.potentialBonusAtCurrentRate shouldBe Some(BigDecimal(475.38))
//      savingsUpdate.bonusUpdate.potentialBonusWithFiveMore  shouldBe None
//    }
//
//    "Calculate maximum bonus possible correctly for user in the first term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          savingsUpdateMobileHelpToSaveAccount.copy(openedYearMonth = YearMonth.now().minusMonths(28)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.maxBonus shouldBe Some(BigDecimal(522.79))
//    }
//
//    "Calculate maximum bonus possible correctly for user in the second term" in {
//      val savingsUpdate =
//        service.getSavingsUpdateResponse(
//          mobileHelpToSaveAccountSecondTerm.copy(openedYearMonth = YearMonth.now().minusMonths(30)),
//          transactionsDateDynamic,
//          dateDynamicSavingsGoalData
//        )
//      savingsUpdate.bonusUpdate.maxBonus shouldBe Some(BigDecimal(172.79))
//    }
//
//  }
//}
