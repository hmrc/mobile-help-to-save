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
import uk.gov.hmrc.mobilehelptosave.domain.Milestones._
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._

trait MilestonesService[F[_]] {
  def setMilestone(milestone: MongoMilestone)(implicit hc: HeaderCarrier): F[Unit]

  def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[MongoMilestone]]

  def markAsSeen(
    nino:        Nino,
    milestoneId: String
  )(implicit hc: HeaderCarrier
  ): F[Unit]
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
      .flatMap(grouped =>
        grouped._2
          .filter(milestone => milestone.generatedDate == grouped._2.map(_.generatedDate).max(localDateTimeOrdering))
      )
      .toList

  override def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[MongoMilestone]] =
    milestonesRepo.getMilestones(nino).map { milestones =>
      val filteredMilestones = filterDuplicateMilestoneTypes(milestones)
      (config.balanceMilestoneCheckEnabled,
       config.bonusPeriodMilestoneCheckEnabled,
       config.bonusReachedMilestoneCheckEnabled) match {
        case (false, false, false) => List.empty
        case (false, true, true)   => filteredMilestones.filter(_.milestoneType != BalanceReached).highestPriority
        case (true, false, true)   => filteredMilestones.filter(_.milestoneType != BonusPeriod).highestPriority
        case (true, true, false)   => filteredMilestones.filter(_.milestoneType != BonusReached).highestPriority
        case (true, false, false)  => filteredMilestones.filter(_.milestoneType == BalanceReached).highestPriority
        case (false, true, false)  => filteredMilestones.filter(_.milestoneType == BonusPeriod).highestPriority
        case (false, false, true)  => filteredMilestones.filter(_.milestoneType == BonusReached).highestPriority
        case _                     => filteredMilestones.highestPriority
      }
    }

  override def setMilestone(milestone: MongoMilestone)(implicit hc: HeaderCarrier): F[Unit] =
    milestonesRepo.setMilestone(milestone)

  override def markAsSeen(
    nino:          Nino,
    milestoneType: String
  )(implicit hc:   HeaderCarrier
  ): F[Unit] = milestonesRepo.markAsSeen(nino, milestoneType)

}
