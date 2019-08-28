/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import cats.MonadError
import cats.syntax.functor._
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.MilestonesConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._
trait BonusPeriodMilestonesService[F[_]] {

  def bonusPeriodMilestoneCheck(nino: Nino, bonusTerms: Seq[BonusTerm], currentBalance: BigDecimal)(
    implicit hc:                      HeaderCarrier): F[MilestoneCheckResult]
}

class HtsBonusPeriodMilestonesService[F[_]](
  logger:              LoggerLike,
  config:              MilestonesConfig,
  milestonesRepo:      MilestonesRepo[F],
  previousBalanceRepo: PreviousBalanceRepo[F]
)(implicit F:          MonadError[F, Throwable])
    extends HtsMilestonesService[F](
      logger:              LoggerLike,
      config:              MilestonesConfig,
      milestonesRepo:      MilestonesRepo[F],
      previousBalanceRepo: PreviousBalanceRepo[F]
    )
    with BonusPeriodMilestonesService[F] {

  override def bonusPeriodMilestoneCheck(nino: Nino, bonusTerms: Seq[BonusTerm], currentBalance: BigDecimal)(
    implicit hc:                               HeaderCarrier): F[MilestoneCheckResult] = {

    val endOfFirstBonusPeriod              = bonusTerms.head.endDate
    val endOfSecondBonusPeriod             = bonusTerms(1).endDate
    val firstPeriodBonusEstimate           = bonusTerms.head.bonusEstimate
    val secondPeriodBonusEstimate          = bonusTerms(1).bonusEstimate
    val firstPeriodBonusPaid               = bonusTerms.head.bonusPaid
    val firstPeriodBonusPaidOnOrAfterDate  = bonusTerms.head.bonusPaidOnOrAfterDate
    val secondPeriodBonusPaidOnOrAfterDate = bonusTerms(1).bonusPaidOnOrAfterDate

    checkBonusPeriods(
      nino,
      endOfFirstBonusPeriod,
      endOfSecondBonusPeriod,
      firstPeriodBonusEstimate,
      secondPeriodBonusEstimate,
      firstPeriodBonusPaid,
      currentBalance,
      firstPeriodBonusPaidOnOrAfterDate,
      secondPeriodBonusPaidOnOrAfterDate
    ) match {
      case Some(milestone) => super.setMilestone(milestone).map(_ => MilestoneHit)
      case _               => F.pure(MilestoneNotHit)
    }
  }

  protected def checkBonusPeriods(
    implicit nino:                      Nino,
    endOfFirstBonusPeriod:              LocalDate,
    endOfSecondBonusPeriod:             LocalDate,
    firstPeriodBonusEstimate:           BigDecimal,
    secondPeriodBonusEstimate:          BigDecimal,
    firstPeriodBonus:                   BigDecimal,
    currentBalance:                     BigDecimal,
    firstPeriodBonusPaidOnOrAfterDate:  LocalDate,
    secondPeriodBonusPaidOnOrAfterDate: LocalDate): Option[MongoMilestone] = {

    def currentDateInDuration(date: LocalDate, duration: Int): Boolean =
      (0 to duration).contains(LocalDate.now().until(date, ChronoUnit.DAYS))

    val hasFirstBonusEstimate              = firstPeriodBonusEstimate > 0
    val hasSecondBonusEstimate             = secondPeriodBonusEstimate > 0
    val firstPeriodBonusPaid               = firstPeriodBonus > 0
    val within20DaysOfFirstPeriodEndDate   = currentDateInDuration(endOfFirstBonusPeriod, 19)
    val under90DaysSinceFirstPeriodEndDate = currentDateInDuration(firstPeriodBonusPaidOnOrAfterDate.plusDays(90), 89)
    val within20DaysOfFinalEndDate         = currentDateInDuration(endOfSecondBonusPeriod, 19)
    val dateFormat                         = DateTimeFormatter.ofPattern("d MMMM yyyy")

    if (within20DaysOfFirstPeriodEndDate && hasFirstBonusEstimate)
      Some(
        createBonusPeriodMongoMilestone(
          EndOfFirstBonusPeriodPositiveBonus,
          Some(
            Map(
              "bonusEstimate"          -> firstPeriodBonusEstimate.toString(),
              "bonusPaidOnOrAfterDate" -> firstPeriodBonusPaidOnOrAfterDate.format(dateFormat)))
        ))
    else if (under90DaysSinceFirstPeriodEndDate && !hasFirstBonusEstimate && !hasSecondBonusEstimate && !firstPeriodBonusPaid)
      Some(
        createBonusPeriodMongoMilestone(
          StartOfFinalBonusPeriodNoBonus
        ))
    else if (within20DaysOfFinalEndDate) {
      if (currentBalance <= 0 && !hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            EndOfFinalBonusPeriodZeroBalanceNoBonus,
            Some(Map("bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.format(dateFormat)))
          ))
      else if (currentBalance <= 0 && hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            EndOfFinalBonusPeriodZeroBalancePositiveBonus,
            Some(
              Map(
                "bonusEstimate"          -> secondPeriodBonusEstimate.toString(),
                "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.format(dateFormat)))
          ))
      else if (currentBalance > 0 && !hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            EndOfFinalBonusPeriodPositiveBalanceNoBonus,
            Some(Map("balance" -> currentBalance.toString(), "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.format(dateFormat)))
          ))
      else
        Some(
          createBonusPeriodMongoMilestone(
            EndOfFinalBonusPeriodPositiveBalancePositiveBonus,
            Some(
              Map(
                "bonusEstimate"          -> secondPeriodBonusEstimate.toString(),
                "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.format(dateFormat),
                "balance"                -> currentBalance.toString()
              ))
          )
        )
    } else None
  }

  private def createBonusPeriodMongoMilestone(milestoneKey: MilestoneKey, values: Option[Map[String, String]] = None)(
    implicit nino:                                          Nino): MongoMilestone =
    MongoMilestone(
      nino          = nino,
      milestoneType = BonusPeriod,
      milestone     = Milestone(milestoneKey, values),
      isRepeatable  = false
    )

}
