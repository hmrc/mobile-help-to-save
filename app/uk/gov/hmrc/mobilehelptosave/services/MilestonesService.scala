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
  def setMilestone(milestone: Milestone)(implicit hc: HeaderCarrier): F[Unit]

  def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[Milestone]]

  def markAsSeen(nino: Nino, milestoneId: String)(implicit hc: HeaderCarrier): F[Unit]

  def balanceMilestoneCheck(nino: Nino, currentBalance: BigDecimal)(implicit hc: HeaderCarrier): F[MilestoneCheckResult]
}

class HtsMilestonesService[F[_]](
  logger:              LoggerLike,
  config:              MilestonesConfig,
  milestonesRepo:      MilestonesRepo[F],
  previousBalanceRepo: PreviousBalanceRepo[F]
)(implicit F:          MonadError[F, Throwable])
    extends MilestonesService[F] {

  protected def filterDuplicateMilestoneTypes(milestones: List[Milestone]): List[Milestone] =
    milestones
      .groupBy(_.milestoneType)
      .flatMap(grouped => grouped._2.filter(milestone => milestone.generatedDate == grouped._2.map(_.generatedDate).max(localDateTimeOrdering)))
      .toList

  override def getMilestones(nino: Nino)(implicit hc: HeaderCarrier): F[List[Milestone]] =
    milestonesRepo.getMilestones(nino).map { milestones =>
      val filteredMilestones = filterDuplicateMilestoneTypes(milestones)

      config.balanceMilestoneCheckEnabled match {
        case true => filteredMilestones
        case _    => filteredMilestones.filter(_.milestoneType != BalanceReached)
      }
    }

  override def setMilestone(milestone: Milestone)(implicit hc: HeaderCarrier): F[Unit] = milestonesRepo.setMilestone(milestone)

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

  protected def compareBalances(nino: Nino, previousBalance: BigDecimal, currentBalance: BigDecimal): Option[Milestone] =
    (previousBalance, currentBalance) match {
      case (_, _) if previousBalance < 1 && currentBalance >= 1 =>
        Some(Milestone(nino = nino, milestoneType = BalanceReached, milestoneKey = StartedSaving, isRepeatable = false))
      case _ => None
    }

}
