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

import java.time.{LocalDate, LocalDateTime}

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

import scala.concurrent.Future

class MilestonesServiceSpec
    extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with FutureAwaits
    with DefaultAwaitTimeout
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with TestF {

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestMilestonesConfig(balanceMilestoneCheckEnabled = true, bonusPeriodMilestoneCheckEnabled = true)
  private val baseBonusTerms = Seq(
    BonusTerm(
      bonusEstimate                 = BigDecimal("90.99"),
      bonusPaid                     = BigDecimal("90.99"),
      endDate                       = LocalDate.now().plusDays(20),
      bonusPaidOnOrAfterDate        = LocalDate.now().plusDays(50),
      balanceMustBeMoreThanForBonus = 0
    ),
    BonusTerm(
      bonusEstimate                 = 12,
      bonusPaid                     = 0,
      endDate                       = LocalDate.now().plusYears(1),
      bonusPaidOnOrAfterDate        = LocalDate.now().plusMonths(13),
      balanceMustBeMoreThanForBonus = BigDecimal("181.98")
    )
  )

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "getMilestones" should {
    "retrieve a list of unseen milestones including a BalanceReached milestone when balanceMilestoneCheckEnabled is set to true" in {
      val milestones = List(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false))

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result shouldBe milestones
    }

    "filter out any milestones with the same milestoneType that have not been seen, only retrieving the most recent one" in {
      val milestone = MongoMilestone(
        nino          = nino,
        milestoneType = BalanceReached,
        milestone  = Milestone(BalanceReached1),
        isRepeatable  = false,
        generatedDate = LocalDateTime.parse("2019-01-16T10:15:30"))

      val milestones = List(
        milestone,
        milestone.copy(generatedDate = LocalDateTime.parse("2019-01-17T10:15:30"))
      )

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result shouldBe List(milestone.copy(generatedDate = LocalDateTime.parse("2019-01-17T10:15:30")))
    }

    "retrieve a list of unseen milestones not including a BalanceReached milestone when balanceMilestoneCheckEnabled is set to false" in {
      val milestones = List(MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false))

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig.copy(balanceMilestoneCheckEnabled = false), milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result shouldBe List.empty
    }
  }

  "setMilestone" should {
    "store the milestone that has been hit by the user" in {
      val milestone = MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.setMilestone(milestone).unsafeGet
      result shouldBe (())
    }
  }

  "balanceMilestoneCheck" should {
    "check if the user's previous balance has been set before and if not, set it and return CouldNotCheck" in {
      val milestone = MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0).unsafeGet
      result shouldBe CouldNotCheck
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneNotHit if the BalanceReached1 milestone has not been hit" in {
      val milestone = MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0).unsafeGet
      result shouldBe MilestoneNotHit
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneHit if the BalanceReached1 milestone has been hit" in {
      val milestone = MongoMilestone(nino = nino, milestoneType = BalanceReached, milestone = Milestone(BalanceReached1), isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 1).unsafeGet
      result shouldBe MilestoneHit
    }
  }

  "markAsSeen" should {
    "mark milestones as seen using the nino and milestone type" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.markAsSeen(nino, "TestMilestoneType").unsafeGet
      result shouldBe (())
    }
  }

  "bonusPeriodMilestoneCheck" should {
    "check if the current date is within 20 days of the bonus period end date and return MilestoneHit if the bonus estimate is greater than 1" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.bonusPeriodMilestoneCheck(nino, baseBonusTerms, 100).unsafeGet
      result shouldBe MilestoneHit
    }

    "check if the current date is within 20 days of the bonus period end date and return MilestoneNotHit if the bonus estimate is 0" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(bonusEstimate = 0), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 200).unsafeGet
      result shouldBe MilestoneNotHit
    }

    "check if the current date is within 20 days of the bonus period end date and if not, then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().plusDays(21)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000).unsafeGet
      result shouldBe MilestoneNotHit
    }

    "check if the current date is 90 or less days since the end of the first bonus period end date and if there are no bonus estimates or paid bonuses, then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().minusDays(1), bonusEstimate = 0,bonusPaid = 0), baseBonusTerms(1).copy(bonusEstimate = 0))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000).unsafeGet
      result shouldBe MilestoneHit
    }

    "check if the current date is 90 or less days since the end of the first bonus period end date and if there are any bonus estimates or paid bonuses, then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().minusDays(1)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000).unsafeGet
      result shouldBe MilestoneNotHit
    }

    "check if the current date is withing 20 days of the second bonus period end date, then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().minusYears(1)), baseBonusTerms(1).copy(endDate = LocalDate.now().plusDays(20)))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000).unsafeGet
      result shouldBe MilestoneHit
    }
  }

  private def fakeMilestonesRepo(milestones: List[MongoMilestone] = List.empty) = new MilestonesRepo[TestF] {
    override def setMilestone(milestone: MongoMilestone): TestF[Unit] = F.unit
    override def getMilestones(nino:     Nino): TestF[List[MongoMilestone]] = F.pure(milestones)
    override def markAsSeen(nino:        Nino, milestoneId: String): TestF[Unit] = F.unit
    override def clearMilestones(): TestF[Unit] = ???
  }

  private def fakePreviousBalanceRepo(previousBalance: Option[PreviousBalance] = None) = new PreviousBalanceRepo[TestF] {
    override def setPreviousBalance(nino: Nino, previousBalance: BigDecimal): TestF[Unit] = F.unit
    override def getPreviousBalance(nino: Nino): TestF[Option[PreviousBalance]] = F.pure(previousBalance)
    override def clearPreviousBalance(): Future[Unit] = ???
  }

}
