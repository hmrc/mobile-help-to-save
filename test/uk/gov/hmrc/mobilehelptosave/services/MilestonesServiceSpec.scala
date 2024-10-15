/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.LoggerLike

import java.time.{Instant, LocalDate, LocalDateTime}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HttpClientV2Helper
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository._
import uk.gov.hmrc.mobilehelptosave.support.TestF

import scala.concurrent.Future

class MilestonessServiceSpec extends HttpClientV2Helper with TestF {

  private val now       = LocalDate.now()
  val logger = mock[LoggerLike]
  private val testConfig =
    TestMilestonesConfig(balanceMilestoneCheckEnabled      = true,
                         bonusPeriodMilestoneCheckEnabled  = true,
                         bonusReachedMilestoneCheckEnabled = true)

  private val baseBonusTerms = Seq(
    BonusTerm(
      bonusEstimate                 = BigDecimal("90.99"),
      bonusPaid                     = BigDecimal("90.99"),
      endDate                       = LocalDate.now().plusDays(19),
      bonusPaidOnOrAfterDate        = LocalDate.now().plusDays(50),
      bonusPaidByDate               = LocalDate.now().plusDays(50),
      balanceMustBeMoreThanForBonus = 0
    ),
    BonusTerm(
      bonusEstimate                 = 12,
      bonusPaid                     = 0,
      endDate                       = LocalDate.now().plusYears(1),
      bonusPaidOnOrAfterDate        = LocalDate.now().plusMonths(13),
      bonusPaidByDate               = LocalDate.now().plusMonths(13),
      balanceMustBeMoreThanForBonus = BigDecimal("181.98")
    )
  )

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "getMilestones" should {
    "retrieve a list of unseen milestones including a BalanceReached milestone when balanceMilestoneCheckEnabled is set to true" in {
      val milestones = List(
        MongoMilestone(nino          = nino,
                       milestoneType = BalanceReached,
                       milestone     = Milestone(BalanceReached1),
                       isRepeatable  = false)
      )

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result mustBe milestones
    }

    "filter out any milestones with the same milestoneType that have not been seen, only retrieving the most recent one" in {
      val milestone = MongoMilestone(
        nino          = nino,
        milestoneType = BalanceReached,
        milestone     = Milestone(BalanceReached1),
        isRepeatable  = false,
        generatedDate = Instant.parse("2019-01-16T10:15:30Z")
      )

      val milestones = List(
        milestone,
        milestone.copy(generatedDate = Instant.parse("2019-01-17T10:15:30Z"))
      )

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result mustBe List(milestone.copy(generatedDate = Instant.parse("2019-01-17T10:15:30Z")))
    }

    "retrieve a list of unseen milestones not including a BalanceReached milestone when balanceMilestoneCheckEnabled is set to false" in {
      val milestones = List(
        MongoMilestone(nino          = nino,
                       milestoneType = BalanceReached,
                       milestone     = Milestone(BalanceReached1),
                       isRepeatable  = false)
      )

      val milestonesRepo      = fakeMilestonesRepo(milestones)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger,
                                 testConfig.copy(balanceMilestoneCheckEnabled = false),
                                 milestonesRepo,
                                 previousBalanceRepo)

      val result = service.getMilestones(nino).unsafeGet
      result mustBe List.empty
    }
  }

  "setMilestone" should {
    "store the milestone that has been hit by the user" in {
      val milestone = MongoMilestone(nino = nino,
                                     milestoneType = BalanceReached,
                                     milestone     = Milestone(BalanceReached1),
                                     isRepeatable  = false)

      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.setMilestone(milestone).unsafeGet
      result mustBe (())
    }
  }

  "balanceMilestoneCheck" should {
    "check if the user's previous balance has been set before and if not, set it and return CouldNotCheck" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsBalanceMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0, now).unsafeGet
      result mustBe CouldNotCheck
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneNotHit if the BalanceReached1 milestone has not been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBalanceMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 0, now).unsafeGet
      result mustBe MilestoneNotHit
    }

    "compare the current and previous balances if the previous balance has been set and return MilestoneHit if the BalanceReached1 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBalanceMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.balanceMilestoneCheck(nino, 1, now).unsafeGet
      result mustBe MilestoneHit
    }
  }

  "markAsSeen" should {
    "mark milestones as seen using the nino and milestone type" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo()

      val service =
        new HtsMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.markAsSeen(nino, "TestMilestoneType").unsafeGet
      result mustBe (())
    }
  }

  "bonusPeriodMilestoneCheck" should {
    "check if the current date is within 20 days of the bonus period end date and return MilestoneHit if the bonus estimate is greater than 1" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.bonusPeriodMilestoneCheck(nino, baseBonusTerms, 100, CurrentBonusTerm.First, false).unsafeGet
      result mustBe MilestoneHit
    }

    "check if the current date is within 20 days of the bonus period end date and return MilestoneNotHit if the bonus estimate is 0" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(bonusEstimate = 0), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 200, CurrentBonusTerm.First, false).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current date is within 20 days of the bonus period end date and if not, then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().plusDays(21)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.First, false).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current date is 90 or less days since the end of the first bonus period end date and if there are no bonus estimates or paid bonuses, then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms = Seq(
        baseBonusTerms(0).copy(bonusPaidOnOrAfterDate = LocalDate.now().minusDays(1),
                               bonusPaidByDate        = LocalDate.now().minusDays(1),
                               bonusEstimate          = 0,
                               bonusPaid              = 0),
        baseBonusTerms(1).copy(bonusEstimate          = 0)
      )

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneHit
    }

    "check if the current date is 90 or less days since the end of the first bonus period end date and if there are any bonus estimates or paid bonuses, then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(baseBonusTerms(0).copy(bonusPaid = 0, endDate = LocalDate.now().minusDays(1)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current date is withing 20 days of the second bonus period end date, then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().minusYears(1)),
            baseBonusTerms(1).copy(endDate = LocalDate.now().plusDays(19)))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneHit
    }

    "check if the current term is after the first and if a first term bonus was paid then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(baseBonusTerms(0).copy(endDate = LocalDate.now().minusDays(1)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneHit
    }

    "check if the current term is after the first and if a first term bonus was not paid then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(baseBonusTerms(0).copy(bonusPaid = 0, endDate = LocalDate.now().minusDays(1)), baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 0, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current term is before the first and if a first term bonus was paid then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(baseBonusTerms(0).copy(bonusEstimate = 0, bonusPaid = 100, endDate = LocalDate.now().plusDays(1)),
            baseBonusTerms(1))

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 0, CurrentBonusTerm.First, false).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current term is after the second and if a final term bonus was paid then return MilestoneHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms(0).copy(bonusPaid = 0, endDate      = LocalDate.now().minusYears(1)),
          baseBonusTerms(1).copy(bonusPaid = 100.00, endDate = LocalDate.now().minusDays(1))
        )

      val result =
        service.bonusPeriodMilestoneCheck(nino, bonusTerms, 1000, CurrentBonusTerm.AfterFinalTerm, true).unsafeGet
      result mustBe MilestoneHit
    }

    "check if the current term is after the second and if a final term bonus was not paid then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms(0).copy(bonusPaid = 0, endDate = LocalDate.now().minusYears(1)),
          baseBonusTerms(1).copy(bonusPaid = 0, endDate = LocalDate.now().minusDays(1))
        )

      val result =
        service.bonusPeriodMilestoneCheck(nino, bonusTerms, 0, CurrentBonusTerm.AfterFinalTerm, true).unsafeGet
      result mustBe MilestoneNotHit
    }

    "check if the current term is the second and if a final term bonus was paid then return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusPeriodMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms(0).copy(bonusPaid     = 0, endDate   = LocalDate.now().minusYears(1)),
          baseBonusTerms(1).copy(bonusEstimate = 0, bonusPaid = 1000, endDate = LocalDate.now().plusDays(21))
        )

      val result = service.bonusPeriodMilestoneCheck(nino, bonusTerms, 0, CurrentBonusTerm.Second, false).unsafeGet
      result mustBe MilestoneNotHit
    }
  }

  "bonusReachedMilestoneCheck" should {
    "If in the first bonus period check the user's estimated first bonus and return MilestoneNotHit if the BalanceReached150 milestone has not been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val result = service.bonusReachedMilestoneCheck(nino, baseBonusTerms, CurrentBonusTerm.First).unsafeGet
      result mustBe MilestoneNotHit
    }

    "If in the first bonus period check the user's estimated first bonus and return MilestoneHit if the BalanceReached150 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(150)),
          baseBonusTerms(1)
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.First).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the first bonus period check the user's estimated first bonus and return MilestoneHit if the BalanceReached300 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(300)),
          baseBonusTerms(1)
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.First).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the first bonus period check the user's estimated first bonus and return MilestoneHit if the BalanceReached600 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1)
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.First).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the second bonus period check the user's estimated second bonus and return MilestoneNotHit if a milestone has not been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1)
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.Second).unsafeGet
      result mustBe MilestoneNotHit
    }

    "If in the second bonus period check the user's estimated second bonus and return MilestoneHit if the BalanceReached75 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1).copy(bonusEstimate   = BigDecimal(75))
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.Second).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the second bonus period check the user's estimated second bonus and return MilestoneHit if the BalanceReached200 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1).copy(bonusEstimate   = BigDecimal(200))
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.Second).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the second bonus period check the user's estimated second bonus and return MilestoneHit if the BalanceReached300 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1).copy(bonusEstimate   = BigDecimal(300))
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.Second).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the second bonus period check the user's estimated second bonus and return MilestoneHit if the BalanceReached500 milestone has been hit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1).copy(bonusEstimate   = BigDecimal(500))
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.Second).unsafeGet
      result mustBe MilestoneHit
    }

    "If in the after final term period return MilestoneNotHit" in {
      val milestonesRepo      = fakeMilestonesRepo(List.empty)
      val previousBalanceRepo = fakePreviousBalanceRepo(Some(PreviousBalance(nino, 0, Instant.now())))

      val service =
        new HtsBonusReachedMilestonesService(logger, testConfig, milestonesRepo, previousBalanceRepo)

      val bonusTerms =
        Seq(
          baseBonusTerms.head.copy(bonusEstimate = BigDecimal(600)),
          baseBonusTerms(1).copy(bonusEstimate   = BigDecimal(500))
        )

      val result = service.bonusReachedMilestoneCheck(nino, bonusTerms, CurrentBonusTerm.AfterFinalTerm).unsafeGet
      result mustBe MilestoneNotHit
    }
  }

  private def fakeMilestonesRepo(milestones: List[MongoMilestone] = List.empty) = new MilestonesRepo[TestF] {
    override def setMilestone(milestone:     MongoMilestone): TestF[Unit] = F.unit
    override def setTestMilestone(milestone: TestMilestone):  TestF[Unit] = F.unit

    override def setTestMilestones(
      milestone: TestMilestone,
      amount:    Int
    ): TestF[Unit] = F.unit
    override def getMilestones(nino: Nino): TestF[List[MongoMilestone]] = F.pure(milestones)

    override def markAsSeen(
      nino:        Nino,
      milestoneId: String
    ):                              TestF[Unit] = F.unit
    override def clearMilestones(): TestF[Unit] = ???

    override def updateExpireAt(
      nino:     Nino,
      expireAt: LocalDateTime
    ): TestF[Unit] = F.unit

    override def updateExpireAt(): TestF[Unit] = F.unit

  }

  private def fakePreviousBalanceRepo(previousBalance: Option[PreviousBalance] = None) =
    new PreviousBalanceRepo[TestF] {

      override def setPreviousBalance(
        nino:                 Nino,
        previousBalance:      BigDecimal,
        finalBonusPaidByDate: LocalDateTime
      ): TestF[Unit] = F.unit
      override def getPreviousBalance(nino: Nino): TestF[Option[PreviousBalance]] = F.pure(previousBalance)

      override def clearPreviousBalance(): Future[Unit] = ???

      override def updateExpireAt(
        nino:     Nino,
        expireAt: LocalDateTime
      ): TestF[Unit] = F.unit

      override def updateExpireAt(): TestF[Unit] = F.unit

      override def getPreviousBalanceUpdateRequired(nino: Nino): TestF[Option[PreviousBalance]] =
        F.pure(previousBalance)

    }

}
