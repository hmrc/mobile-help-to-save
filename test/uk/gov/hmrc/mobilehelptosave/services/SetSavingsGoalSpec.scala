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

import cats.syntax.either._
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{EitherValues, Matchers, OneInstancePerTest, WordSpec}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, Milestone, MilestoneCheckResult, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

class SetSavingsGoalSpec
    extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with TestF
    with AccountTestData
    with EitherValues
    with MockFactory
    with OneInstancePerTest
    with LoggerStub {

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "setSavingsGoal" should {
    "return Right[Unit] if successful" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount      = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalsRepo       = fakeSavingsGoalEventsRepo(nino)

      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          fakeGoalsRepo,
          fakeMilestoneService)

      service.setSavingsGoal(nino, SavingsGoal(1.0)).unsafeGet shouldBe Right(())
    }

    "return a ValidationError if the goal is < 1.0" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)

      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          savingsGoalEventRepo,
          fakeMilestoneService)

      service.setSavingsGoal(nino, SavingsGoal(0.99)).unsafeGet.left.value shouldBe a[ErrorInfo.ValidationError]
    }

    "return a ValidationError if the goal is > maximum for month" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)

      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          savingsGoalEventRepo,
          fakeMilestoneService)

      service
        .setSavingsGoal(nino, SavingsGoal(helpToSaveAccount.maximumPaidInThisMonth.toDouble + 0.01))
        .unsafeGet
        .left
        .value shouldBe a[ErrorInfo.ValidationError]
    }

    "return an AccountNotFound if the nino does not have an account with NS&I" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(None))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino)

      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          savingsGoalEventRepo,
          fakeMilestoneService)

      service.setSavingsGoal(nino, SavingsGoal(1.0)).unsafeGet.left.value shouldBe ErrorInfo.AccountNotFound
    }

    "return a General error if the repo throws a Non-Fatal exception" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, setGoalResponse = Left(new Exception("non fatal")))

      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          savingsGoalEventRepo,
          fakeMilestoneService)

      service.setSavingsGoal(nino, SavingsGoal(1.0)).unsafeGet.left.value shouldBe ErrorInfo.General
    }
  }

  private def fakeMilestoneService: MilestonesService[TestF] =
    new MilestonesService[TestF] {
      override def setMilestone(milestone:     Milestone)(implicit hc: HeaderCarrier): TestF[Unit] = ???
      override def getMilestones(nino:         Nino)(implicit hc:      HeaderCarrier): TestF[List[Milestone]] = ???
      override def markAsSeen(nino:            Nino, milestoneId:      String)(implicit hc: HeaderCarrier): TestF[Unit] = ???
      override def balanceMilestoneCheck(nino: Nino, currentBalance:   BigDecimal)(implicit hc: HeaderCarrier): TestF[MilestoneCheckResult] =
        ???
    }

  private def fakeHelpToSaveEnrolmentStatus(expectedNino: Nino, enrolledOrError: Either[ErrorInfo, Boolean]): HelpToSaveEnrolmentStatus[TestF] =
    new HelpToSaveEnrolmentStatus[TestF] {
      override def enrolmentStatus()(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Boolean]] = {
        nino shouldBe expectedNino
        hc   shouldBe passedHc

        F.pure(enrolledOrError)
      }
    }

  private def fakeHelpToSaveGetAccount(
    expectedNino:   Nino,
    accountOrError: Either[ErrorInfo, Option[HelpToSaveAccount]]): HelpToSaveGetAccount[TestF] =
    new HelpToSaveGetAccount[TestF] {
      override def getAccount(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
        nino shouldBe expectedNino
        hc   shouldBe passedHc

        F.pure(accountOrError)
      }
    }

  private val fUnit = F.unit

  private def fakeSavingsGoalEventsRepo(
    expectedNino:       Nino,
    goalsOrException:   Either[Throwable, List[SavingsGoalEvent]] = List().asRight,
    setGoalResponse:    Either[Throwable, Unit] = ().asRight,
    deleteGoalResponse: Either[Throwable, Unit] = ().asRight
  ): SavingsGoalEventRepo[TestF] = new SavingsGoalEventRepo[TestF] {
    override def setGoal(nino: Nino, amount: Double): TestF[Unit] = {
      nino shouldBe expectedNino
      setGoalResponse match {
        case Left(t)  => F.raiseError(t)
        case Right(_) => fUnit
      }
    }

    override def getEvents(nino: Nino): TestF[List[SavingsGoalEvent]] = {
      nino shouldBe expectedNino
      goalsOrException match {
        case Left(t)     => F.raiseError(t)
        case Right(goal) => F.pure(goal)
      }
    }

    override def deleteGoal(nino: Nino): TestF[Unit] = {
      nino shouldBe expectedNino
      deleteGoalResponse match {
        case Left(t)  => F.raiseError(t)
        case Right(_) => fUnit
      }
    }

    override def clearGoalEvents(): TestF[Boolean] = F.pure(true)

    // These should never get called as part of setting a savings goal
    override def getGoal(nino: Nino): TestF[Option[SavingsGoal]] = ???
    override def getGoalSetEvents(): TestF[List[SavingsGoalSetEvent]] = ???
  }

  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount[TestF] {
    override def getAccount(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
      F.raiseError(new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation"))
  }
}
