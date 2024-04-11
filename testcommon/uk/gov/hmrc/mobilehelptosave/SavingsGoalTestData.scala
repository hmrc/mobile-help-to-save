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

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalSetEvent

import java.time.{Instant, LocalDateTime, ZoneOffset}

trait SavingsGoalTestData {

  protected val dateDynamicSavingsGoalData: Seq[SavingsGoalSetEvent] = Seq(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(10), Instant.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(15), currentDateMinusMonths(1)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(20), currentDateMinusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), currentDateMinusMonths(8)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(45), currentDateMinusMonths(9))
  )

  protected val noChangeInPeriodSavingsGoalData: List[SavingsGoalSetEvent] = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), currentDateMinusMonths(12)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(45), currentDateMinusMonths(19))
  )

  protected val singleChangeSavingsGoalData: List[SavingsGoalSetEvent] = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), Instant.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(20), currentDateMinusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), currentDateMinusMonths(8))
  )

  protected val multipleChangeSavingsGoalData: List[SavingsGoalSetEvent] = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), Instant.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(100), currentDateMinusMonths(1)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), currentDateMinusMonths(4)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(12.50), currentDateMinusMonths(8))
  )

  protected val multipleChangeInMonthSavingsGoalData: List[SavingsGoalSetEvent] = List(
    SavingsGoalSetEvent(Nino("CS700100A"), Some(50), Instant.now()),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(1), currentDateMinusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(30), currentDateMinusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(40), currentDateMinusMonths(3)),
    SavingsGoalSetEvent(Nino("CS700100A"), Some(25), currentDateMinusMonths(8))
  )

  private def currentDateMinusMonths(monthsToMinus: Int): Instant =
    LocalDateTime.now(ZoneOffset.UTC).minusMonths(monthsToMinus).toInstant(ZoneOffset.UTC)

}
