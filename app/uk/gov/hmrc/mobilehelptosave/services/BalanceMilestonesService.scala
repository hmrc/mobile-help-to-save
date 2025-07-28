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

import cats.MonadError
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import play.api.LoggerLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.MilestonesConfig
import uk.gov.hmrc.mobilehelptosave.domain.*
import uk.gov.hmrc.mobilehelptosave.repository.*

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

trait BalanceMilestonesService {

  def balanceMilestoneCheck(
    nino: Nino,
    currentBalance: BigDecimal,
    secondPeriodBonusPaidByDate: LocalDate
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[MilestoneCheckResult]

}

class HtsBalanceMilestonesService(
  logger: LoggerLike,
  config: MilestonesConfig,
  milestonesRepo: MilestonesRepo,
  previousBalanceRepo: PreviousBalanceRepo
) extends HtsMilestonesService(
      logger: LoggerLike,
      config: MilestonesConfig,
      milestonesRepo: MilestonesRepo,
      previousBalanceRepo: PreviousBalanceRepo
    )
    with BalanceMilestonesService {

  override def balanceMilestoneCheck(
    nino: Nino,
    currentBalance: BigDecimal,
    secondPeriodBonusPaidByDate: LocalDate
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[MilestoneCheckResult] =
    previousBalanceRepo.getPreviousBalance(nino) flatMap {
      case Some(pb) =>
        previousBalanceRepo
          .setPreviousBalance(nino, currentBalance, secondPeriodBonusPaidByDate.atStartOfDay())
          .flatMap(_ =>
            compareBalances(nino, pb.previousBalance, currentBalance, secondPeriodBonusPaidByDate) match {
              case Some(milestone) => setMilestone(milestone).map(_ => MilestoneHit)
              case _               => Future.successful(MilestoneNotHit)
            }
          )
      case _ =>
        previousBalanceRepo
          .setPreviousBalance(nino, currentBalance, secondPeriodBonusPaidByDate.atStartOfDay())
          .map(_ => CouldNotCheck)
    }

  protected def compareBalances(implicit
    nino: Nino,
    previousBalance: BigDecimal,
    currentBalance: BigDecimal,
    secondPeriodBonusPaidByDate: LocalDate
  ): Option[MongoMilestone] = {
    def inRange(
      min: BigDecimal,
      max: BigDecimal
    ): Boolean =
      previousBalance < min && currentBalance >= min && currentBalance < max

    def reached(value: BigDecimal): Boolean =
      previousBalance < value && currentBalance >= value

    (previousBalance, currentBalance) match {
      case (_, _) if inRange(1, 100) =>
        Some(
          createBalanceMongoMilestone(milestoneKey         = BalanceReached1,
                                      finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay(),
                                      isRepeatable         = false
                                     )
        )
      case (_, _) if inRange(100, 200) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached100, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(200, 500) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached200, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(500, 750) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached500, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(750, 1000) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached750, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(1000, 1500) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached1000, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(1500, 2000) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached1500, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if inRange(2000, 2400) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached2000, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )
      case (_, _) if reached(2400) =>
        Some(
          createBalanceMongoMilestone(milestoneKey = BalanceReached2400, finalBonusPaidByDate = secondPeriodBonusPaidByDate.atStartOfDay())
        )

      case _ => None
    }
  }

  private def createBalanceMongoMilestone(
    milestoneKey: MilestoneKey,
    finalBonusPaidByDate: LocalDateTime,
    isRepeatable: Boolean = true
  )(implicit nino: Nino): MongoMilestone =
    MongoMilestone(
      nino          = nino,
      milestoneType = BalanceReached,
      milestone     = Milestone(milestoneKey),
      isRepeatable  = isRepeatable,
      expireAt      = finalBonusPaidByDate.plusMonths(6).toInstant(ZoneOffset.UTC)
    )

}
