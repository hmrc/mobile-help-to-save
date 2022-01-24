/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.MonadError
import cats.syntax.functor._
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.MilestonesConfig
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._

trait BonusReachedMilestonesService[F[_]] {

  def bonusReachedMilestoneCheck(
    nino:             Nino,
    bonusTerms:       Seq[BonusTerm],
    currentBonusTerm: CurrentBonusTerm.Value
  )(implicit hc:      HeaderCarrier
  ): F[MilestoneCheckResult]
}

class HtsBonusReachedMilestonesService[F[_]](
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
    with BonusReachedMilestonesService[F] {

  override def bonusReachedMilestoneCheck(
    nino:             Nino,
    bonusTerms:       Seq[BonusTerm],
    currentBonusTerm: CurrentBonusTerm.Value
  )(implicit hc:      HeaderCarrier
  ): F[MilestoneCheckResult] = {

    val firstPeriodBonusEstimate  = bonusTerms.head.bonusEstimate
    val secondPeriodBonusEstimate = bonusTerms(1).bonusEstimate

    checkBonusReached(
      nino,
      firstPeriodBonusEstimate,
      secondPeriodBonusEstimate,
      currentBonusTerm
    ) match {
      case Some(milestone) => super.setMilestone(milestone).map(_ => MilestoneHit)
      case _               => F.pure(MilestoneNotHit)
    }
  }

  protected def checkBonusReached(
    implicit nino:             Nino,
    firstPeriodBonusEstimate:  BigDecimal,
    secondPeriodBonusEstimate: BigDecimal,
    currentBonusTerm:          CurrentBonusTerm.Value
  ): Option[MongoMilestone] =
    (currentBonusTerm, firstPeriodBonusEstimate, secondPeriodBonusEstimate) match {
      case (CurrentBonusTerm.First, firstBonusEstimate, _) if inRange(150, 300, firstBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FirstBonusReached150))
      case (CurrentBonusTerm.First, firstBonusEstimate, _) if inRange(300, 600, firstBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FirstBonusReached300))
      case (CurrentBonusTerm.First, firstBonusEstimate, _) if reached(600, firstBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FirstBonusReached600))
      case (CurrentBonusTerm.Second, _, secondBonusEstimate) if inRange(75, 200, secondBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FinalBonusReached75))
      case (CurrentBonusTerm.Second, _, secondBonusEstimate) if inRange(200, 300, secondBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FinalBonusReached200))
      case (CurrentBonusTerm.Second, _, secondBonusEstimate) if inRange(300, 500, secondBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FinalBonusReached300))
      case (CurrentBonusTerm.Second, _, secondBonusEstimate) if reached(500, secondBonusEstimate) =>
        Some(createBonusReachedMongoMilestone(FinalBonusReached500))
      case _ => None
    }

  private def inRange(
    min:   BigDecimal,
    max:   BigDecimal,
    value: BigDecimal
  ): Boolean = value >= min && value < max

  private def reached(
    threshold: BigDecimal,
    value:     BigDecimal
  ): Boolean =
    value >= threshold

  private def createBonusReachedMongoMilestone(
    milestoneKey:  MilestoneKey,
    values:        Option[Map[String, String]] = None
  )(implicit nino: Nino
  ): MongoMilestone =
    MongoMilestone(
      nino          = nino,
      milestoneType = BonusReached,
      milestone     = Milestone(milestoneKey, values),
      isRepeatable  = false
    )

}
