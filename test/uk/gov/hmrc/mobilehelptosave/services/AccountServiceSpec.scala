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

import cats.instances.either._
import cats.syntax.applicativeError._
import cats.syntax.functor._
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, Milestone, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

class AccountServiceSpec
    extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with AccountTestData
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with TestF {

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "convert the account from the help-to-save domain to the mobile-help-to-save domain" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    "fold the value of the 'savingsGoalEnabled' config into the returned account" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      forAll { enabled: Boolean =>
        val config = testConfig.copy(savingsGoalsEnabled = enabled)
        val service =
          new HtsAccountService[TestF](logger, config, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)

        // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
        val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
        result shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = enabled)))
      }
    }

    "allow inAppPaymentsEnabled to be overridden with configuration" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](
          logger,
          testConfig.copy(inAppPaymentsEnabled = true),
          fakeEnrolmentStatus,
          fakeGetAccount,
          savingsGoalEventRepo,
          fakeMilestoneService)

      // Because the service uses the system time to calculate the number of remaining days we need to adjust that in the result
      val result = service.account(nino).unsafeGet.map(_.map(_.copy(daysRemainingInMonth = 1)))
      result shouldBe Right(
        Some(mobileHelpToSaveAccount
          .copy(inAppPaymentsEnabled = true, savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    // this is to avoid unnecessary load on NS&I, see NGC-3799
    "return None without attempting to get account from help-to-save when the user is not enrolled" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(false))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, ShouldNotBeCalledGetAccount, savingsGoalEventRepo, fakeMilestoneService)
      service.account(nino).unsafeGet shouldBe Right(None)

      (slf4jLoggerStub.warn(_: String)) verify * never ()
    }

    "return None and log a warning when user is enrolled according to help-to-save but no account exists in NS&I" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(None))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)

      service.account(nino).unsafeGet shouldBe Right(None)

      (slf4jLoggerStub
        .warn(_: String)) verify s"${nino.value} was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
    }

    "not call either fetchSavingsGoal or fetchNSAndIAccount if the user isn't enrolled" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(false))

      val fakeGetAccount = new HelpToSaveGetAccount[TestF] {
        override def getAccount(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
          fail("getAccount should not have been called")
      }

      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)

      service.account(nino).unsafeGet shouldBe Right(None)
    }

    "return errors returned by connector.enrolmentStatus" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Left(ErrorInfo.General))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }

    "return errors returned by connector.getAccount" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Left(ErrorInfo.General))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }

    "return errors if savingsGoalRepo.get throws exception" in {
      val fakeEnrolmentStatus  = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount       = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Left(new Exception("test exception")))
      val service =
        new HtsAccountService[TestF](logger, testConfig, fakeEnrolmentStatus, fakeGetAccount, savingsGoalEventRepo, fakeMilestoneService)
      service.account(nino).unsafeGet shouldBe Left(ErrorInfo.General)
    }
  }

  private def fakeMilestoneService: MilestonesService[TestF] =
    new MilestonesService[TestF] {
      override def setMilestone(milestone:     Milestone)(implicit hc: HeaderCarrier): TestF[Unit] = ???
      override def getMilestones(nino:         Nino)(implicit hc:      HeaderCarrier): TestF[List[Milestone]] = ???
      override def markAsSeen(milestoneId:     String)(implicit hc:    HeaderCarrier): TestF[Unit] = ???
      override def balanceMilestoneCheck(nino: Nino, currentBalance:   BigDecimal)(implicit hc: HeaderCarrier): TestF[Unit] = F.pure(())
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

  private def fakeSavingsGoalEventsRepo(
    expectedNino:     Nino,
    goalsOrException: Either[Throwable, List[SavingsGoalEvent]]): SavingsGoalEventRepo[TestF] =
    new SavingsGoalEventRepo[TestF] {
      override def setGoal(nino: Nino, amount: Double): TestF[Unit] = {
        nino shouldBe expectedNino
        F.unit
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
        F.unit
      }

      override def clearGoalEvents(): TestF[Boolean] = F.pure(true)
      override def getGoal(nino: Nino): TestF[Option[SavingsGoal]] =
        goalsOrException match {
          case Right(events) =>
            F.pure {
              events.sortBy(_.date)(localDateTimeOrdering.reverse).headOption.flatMap {
                case SavingsGoalSetEvent(_, amount, _) => Some(SavingsGoal(amount))
                case _                                 => None
              }
            }
          case Left(t) => F.raiseError(t)
        }

      // This should never get called as part of the account service
      override def getGoalSetEvents(): TestF[List[SavingsGoalSetEvent]] = ???
    }

  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount[TestF] {
    override def getAccount(nino: Nino)(implicit hc: HeaderCarrier): TestF[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
      new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation")
        .raiseError[TestF, Either[ErrorInfo, Option[HelpToSaveAccount]]]
  }
}
