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

package uk.gov.hmrc.mobilehelptosave.services

import uk.gov.hmrc.helptosavecalculator.{FinalBonusTermCalculator, FirstBonusTermCalculator}
import uk.gov.hmrc.helptosavecalculator.models.{FinalBonusCalculatorResponse, FinalBonusInput, FirstBonusCalculatorResponse, FirstBonusInput, YearMonthDayInput}
import uk.gov.hmrc.mobilehelptosave.domain.{Account, BonusTerm}

import java.time.LocalDate

class CalculatorService(account: Account, regularSavings: Double) {
  val now: LocalDate = LocalDate.now()
  val finalBonusTerms: BonusTerm = account.bonusTerms.last

  def getFirstBonusTermCalculation: FirstBonusCalculatorResponse =
    FirstBonusTermCalculator.INSTANCE
      .runFirstBonusCalculator(getFirstBonusInput(account, regularSavings))

  def getFinalBonusTermCalculation: FinalBonusCalculatorResponse =
    FinalBonusTermCalculator.INSTANCE
      .runFinalBonusCalculator(getFinalBonusInput(account, regularSavings))

  private def toYearMonthDayInput(date: LocalDate): YearMonthDayInput =
    new YearMonthDayInput(date.getYear, date.getMonthValue, date.lengthOfMonth())

  private def getFirstBonusInput(
    account: Account,
    averageSavingsRate: Double
  ): FirstBonusInput = {

    val firstBonusTerms = account.bonusTerms.head
    new FirstBonusInput(
      averageSavingsRate,
      account.balance.toDouble,
      account.paidInThisMonth.toDouble,
      toYearMonthDayInput(now),
      toYearMonthDayInput(firstBonusTerms.endDate),
      toYearMonthDayInput(finalBonusTerms.endDate),
      finalBonusTerms.balanceMustBeMoreThanForBonus.toDouble
    )
  }

  private def getFinalBonusInput(
    account: Account,
    averageSavingsRate: Double
  ): FinalBonusInput =
    new FinalBonusInput(
      averageSavingsRate,
      account.balance.toDouble,
      account.paidInThisMonth.toDouble,
      account.canPayInThisMonth.toDouble,
      toYearMonthDayInput(now),
      toYearMonthDayInput(finalBonusTerms.endDate),
      finalBonusTerms.balanceMustBeMoreThanForBonus.toDouble,
      finalBonusTerms.bonusEstimate.toDouble
    )

}
