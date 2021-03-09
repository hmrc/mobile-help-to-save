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

package uk.gov.hmrc.mobilehelptosave.domain

import play.api.libs.json.{Format, Json}

import java.time.{LocalDate, YearMonth}

case class SavingsUpdateResponse(
  reportStartDate:        LocalDate,
  reportEndDate:          LocalDate,
  accountOpenedYearMonth: YearMonth,
  savingsUpdate:          Option[SavingsUpdate],
  bonusUpdate:            BonusUpdate)

object SavingsUpdateResponse {
  implicit val yearMonthFormat: Format[YearMonth]             = uk.gov.hmrc.mobilehelptosave.json.Formats.YearMonthFormat
  implicit val format:          Format[SavingsUpdateResponse] = Json.format[SavingsUpdateResponse]
}

case class SavingsUpdate(
  savedInPeriod:            Option[BigDecimal],
  savedByMonth:             Option[SavedByMonth],
  goalsReached:             Option[GoalsReached],
  amountEarnedTowardsBonus: Option[Double])

object SavingsUpdate {
  implicit val format: Format[SavingsUpdate] = Json.format[SavingsUpdate]
}

case class BonusUpdate(
  currentBonusTerm:            CurrentBonusTerm.Value,
  monthsUntilBonus:            Option[Int],
  currentBonus:                Option[BigDecimal],
  highestBalance:              Option[BigDecimal],
  potentialBonusAtCurrentRate: Option[BigDecimal],
  potentialBonusWithFiveMore:  Option[BigDecimal],
  maxBonus:                    Option[BigDecimal])

object BonusUpdate {
  implicit val format: Format[BonusUpdate] = Json.format[BonusUpdate]
}

case class SavedByMonth(
  numberOfMonths: Int,
  monthsSaved:    Int)

object SavedByMonth {
  implicit val format: Format[SavedByMonth] = Json.format[SavedByMonth]
}

case class GoalsReached(
  currentAmount:        Double,
  currentGoalName:      Option[String],
  numberOfTimesReached: Int)

object GoalsReached {
  implicit val format: Format[GoalsReached] = Json.format[GoalsReached]
}
