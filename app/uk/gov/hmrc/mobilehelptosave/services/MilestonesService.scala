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
import cats.syntax.flatMap._
import cats.syntax.functor._
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.MilestonesConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._
import uk.gov.hmrc.mobilehelptosave.domain.Milestones._
trait MilestonesService[F[_]] {
  def setMilestone(milestone: MongoMilestone)(implicit hc: HeaderCarrier): F[Unit]

  def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[MongoMilestone]]

  def markAsSeen(nino: Nino, milestoneId: String)(implicit hc: HeaderCarrier): F[Unit]

  def balanceMilestoneCheck(nino: Nino, currentBalance: BigDecimal)(implicit hc: HeaderCarrier): F[MilestoneCheckResult]

  def bonusPeriodMilestoneCheck(nino: Nino, bonusTerms: Seq[BonusTerm], currentBalance: BigDecimal)(
    implicit hc:                      HeaderCarrier): F[MilestoneCheckResult]
}

class HtsMilestonesService[F[_]](
  logger:              LoggerLike,
  config:              MilestonesConfig,
  milestonesRepo:      MilestonesRepo[F],
  previousBalanceRepo: PreviousBalanceRepo[F]
)(implicit F:          MonadError[F, Throwable])
    extends MilestonesService[F] {

  protected def filterDuplicateMilestoneTypes(milestones: List[MongoMilestone]): List[MongoMilestone] =
    milestones
      .groupBy(_.milestoneType)
      .flatMap(grouped => grouped._2.filter(milestone => milestone.generatedDate == grouped._2.map(_.generatedDate).max(localDateTimeOrdering)))
      .toList

  override def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[MongoMilestone]] =
    milestonesRepo.getMilestones(nino).map { milestones =>
      val filteredMilestones = filterDuplicateMilestoneTypes(milestones)
      (config.balanceMilestoneCheckEnabled, config.bonusPeriodMilestoneCheckEnabled) match {
        case (false, false) => List.empty
        case (true, false)  => filteredMilestones.filter(_.milestoneType != BonusPeriod).highestPriority
        case (false, true)  => filteredMilestones.filter(_.milestoneType != BalanceReached).highestPriority
        case _              => filteredMilestones.highestPriority
      }
    }

  override def setMilestone(milestone: MongoMilestone)(implicit hc: HeaderCarrier): F[Unit] = milestonesRepo.setMilestone(milestone)

  override def markAsSeen(nino: Nino, milestoneType: String)(implicit hc: HeaderCarrier): F[Unit] = milestonesRepo.markAsSeen(nino, milestoneType)

  override def balanceMilestoneCheck(nino: Nino, currentBalance: BigDecimal)(implicit hc: HeaderCarrier): F[MilestoneCheckResult] =
    previousBalanceRepo.getPreviousBalance(nino) flatMap {
      case Some(pb) =>
        previousBalanceRepo
          .setPreviousBalance(nino, currentBalance)
          .flatMap(_ =>
            compareBalances(nino, pb.previousBalance, currentBalance) match {
              case Some(milestone) => setMilestone(milestone).map(_ => MilestoneHit)
              case _               => F.pure(MilestoneNotHit)
          })
      case _ => previousBalanceRepo.setPreviousBalance(nino, currentBalance).map(_ => CouldNotCheck)
    }

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
      case Some(milestone) => setMilestone(milestone).map(_ => MilestoneHit)
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

  protected def compareBalances(nino: Nino, previousBalance: BigDecimal, currentBalance: BigDecimal): Option[MongoMilestone] = {
    def inRange(min: BigDecimal, max: BigDecimal): Boolean =
      previousBalance < min && currentBalance >= min && currentBalance < max

    def reached(value: BigDecimal): Boolean =
      previousBalance < value && currentBalance >= value

    (previousBalance, currentBalance) match {
      case (_, _) if inRange(1, 100) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false))
      case (_, _) if inRange(100, 200) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached100)))
      case (_, _) if inRange(200, 500) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached200)))
      case (_, _) if inRange(500, 750) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached500)))
      case (_, _) if inRange(750, 1000) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached750)))
      case (_, _) if inRange(1000, 1500) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1000)))
      case (_, _) if inRange(1500, 2000) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1500)))
      case (_, _) if inRange(2000, 2400) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached2000)))
      case (_, _) if reached(2400) =>
        Some(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached2400)))
      case _ => None
    }
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
