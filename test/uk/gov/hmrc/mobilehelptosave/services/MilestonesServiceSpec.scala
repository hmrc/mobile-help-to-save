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

import java.time.LocalDateTime

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

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
  private val testConfig = TestMilestonesServiceConfig(startedSavingMilestoneEnabled = true)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "getMilestones" should {
    "retrieve a list of unseen milestones including a StartedSaving milestone when startedSavingMilestoneEnabled is set to true" in {
      val milestones = List(Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false))

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result shouldBe milestones
    }

    "retrieve a list of unseen milestones not including a StartedSaving milestone when startedSavingMilestoneEnabled is set to false" in {
      val milestones = List(Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false))

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig.copy(startedSavingMilestoneEnabled = false), milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result shouldBe List.empty
    }
  }

  "setMilestone" should {
    "store the milestone that has been hit by the user" in {
      val milestone = Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false)

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
      val milestone = Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0).unsafeGet
      result shouldBe CouldNotCheck
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneNotHit if the StartedSaving milestone has not been hit" in {
      val milestone = Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0).unsafeGet
      result shouldBe MilestoneNotHit
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneHit if the StartedSaving milestone has been hit" in {
      val milestone = Milestone(nino = nino, milestoneType = StartedSaving, isRepeatable = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, LocalDateTime.now())))

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 1).unsafeGet
      result shouldBe MilestoneHit
    }
  }

  private def fakeMilestonesRepo(milestones: List[Milestone] = List.empty) = new MilestonesRepo[TestF] {
    override def setMilestone(milestone: Milestone): TestF[Unit]            = F.unit
    override def getMilestones(nino:     Nino):      TestF[List[Milestone]] = F.pure(milestones)
    override def markAsSeen(milestoneId: String):    TestF[Unit]            = ???
  }

  private def fakePreviousBalanceRepo(previousBalance: Option[PreviousBalance] = None) = new PreviousBalanceRepo[TestF] {
    override def setPreviousBalance(nino: Nino, previousBalance: BigDecimal): TestF[Unit] = F.unit
    override def getPreviousBalance(nino: Nino): TestF[Option[PreviousBalance]] = F.pure(previousBalance)
  }

}
