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

      config.balanceMilestoneCheckEnabled match {
        case true => filteredMilestones
        case _    => filteredMilestones.filter(_.milestoneType != BalanceReached)
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

    val endOfFirstBonusPeriod              = bonusTerms(0).endDate
    val endOfSecondBonusPeriod             = bonusTerms(1).endDate
    val firstPeriodBonusEstimate           = bonusTerms(0).bonusEstimate
    val secondPeriodBonusEstimate          = bonusTerms(1).bonusEstimate
    val firstPeriodBonusPaid               = bonusTerms(0).bonusPaid
    val firstPeriodBonusPaidOnOrAfterDate  = bonusTerms(0).bonusPaidOnOrAfterDate
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
    nino:                               Nino,
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
    val within20DaysOfFirstPeriodEndDate   = currentDateInDuration(endOfFirstBonusPeriod, 20)
    val under90DaysSinceFirstPeriodEndDate = currentDateInDuration(endOfFirstBonusPeriod.plusDays(90), 90)
    val within20DaysOfFinalEndDate         = currentDateInDuration(endOfSecondBonusPeriod, 20)

    if (within20DaysOfFirstPeriodEndDate && hasFirstBonusEstimate)
      Some(
        createBonusPeriodMongoMilestone(
          nino,
          EndOfFirstBonusPeriodPositiveBonus,
          Some(Map("bonusEstimate" -> firstPeriodBonusEstimate.toString(), "bonusPaidOnOrAfterDate" -> firstPeriodBonusPaidOnOrAfterDate.toString))
        ))
    else if (under90DaysSinceFirstPeriodEndDate && !hasFirstBonusEstimate && !hasSecondBonusEstimate && !firstPeriodBonusPaid)
      Some(
        createBonusPeriodMongoMilestone(
          nino,
          StartOfFinalBonusPeriodNoBonus
        ))
    else if (within20DaysOfFinalEndDate) {
      if (currentBalance <= 0 && hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            nino,
            EndOfFinalBonusPeriodZeroBalanceNoBonus,
            Some(Map("bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.toString))
          ))
      else if (currentBalance <= 0 && !hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            nino,
            EndOfFinalBonusPeriodZeroBalancePositiveBonus,
            Some(
              Map("bonusEstimate" -> secondPeriodBonusEstimate.toString(), "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.toString))
          ))
      else if (currentBalance > 0 && hasSecondBonusEstimate)
        Some(
          createBonusPeriodMongoMilestone(
            nino,
            EndOfFinalBonusPeriodPositiveBalanceNoBonus,
            Some(Map("balance" -> currentBalance.toString(), "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.toString))
          ))
      else
        Some(
          createBonusPeriodMongoMilestone(
            nino,
            EndOfFinalBonusPeriodZeroBalancePositiveBonus,
            Some(
              Map(
                "bonusEstimate"          -> secondPeriodBonusEstimate.toString(),
                "bonusPaidOnOrAfterDate" -> secondPeriodBonusPaidOnOrAfterDate.toString,
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

  private def createBonusPeriodMongoMilestone(nino: Nino, milestoneKey: MilestoneKey, values: Option[Map[String, String]] = None): MongoMilestone =
    MongoMilestone(
      nino          = nino,
      milestoneType = BonusPeriod,
      milestone     = Milestone(milestoneKey, values),
      isRepeatable  = false
    )

}
