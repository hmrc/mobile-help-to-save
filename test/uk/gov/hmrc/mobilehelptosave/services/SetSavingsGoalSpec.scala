/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{EitherValues, Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class SetSavingsGoalSpec
  extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with FutureAwaits
    with DefaultAwaitTimeout
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
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig.copy(inAppPaymentsEnabled = true), savingsGoalEventRepo)

      await(service.setSavingsGoal(nino, SavingsGoal(1.0))) shouldBe Right(())
    }

    "return a ValidationError if the goal is < 1.0" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig.copy(inAppPaymentsEnabled = true), savingsGoalEventRepo)

      await(service.setSavingsGoal(nino, SavingsGoal(0.99))).left.value shouldBe a[ErrorInfo.ValidationError]
    }

    "return a ValidationError if the goal is > maximum for month" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig.copy(inAppPaymentsEnabled = true), savingsGoalEventRepo)

      await(service.setSavingsGoal(nino, SavingsGoal(helpToSaveAccount.maximumPaidInThisMonth.toDouble + 0.01))).left.value shouldBe a[ErrorInfo.ValidationError]
    }

    "return an AccountNotFound if the nino does not have an account with NS&I" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(None))
      val savingsGoalEventRepo = fakeSavingsGoalEventsRepo(nino, Right(List()))

      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig.copy(inAppPaymentsEnabled = true), savingsGoalEventRepo)

      await(service.setSavingsGoal(nino, SavingsGoal(1.0))).left.value shouldBe ErrorInfo.AccountNotFound
    }
  }

  private def fakeHelpToSaveEnrolmentStatus(expectedNino: Nino, enrolledOrError: Either[ErrorInfo, Boolean]): HelpToSaveEnrolmentStatus =
    new HelpToSaveEnrolmentStatus {
      override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
        nino shouldBe expectedNino
        hc shouldBe passedHc
        ec shouldBe passedEc

        Future successful enrolledOrError
      }
    }

  private def fakeHelpToSaveGetAccount(expectedNino: Nino, accountOrError: Either[ErrorInfo, Option[HelpToSaveAccount]]): HelpToSaveGetAccount = new HelpToSaveGetAccount {
    override def getAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful accountOrError
    }
  }

  private val fUnit = Future.successful(())

  private def fakeSavingsGoalEventsRepo(expectedNino: Nino, goalsOrException: Either[Throwable, List[SavingsGoalEvent]]): SavingsGoalEventRepo =
    new SavingsGoalEventRepo {
      override def setGoal(nino: Nino, amount: Double): Future[Unit] = {
        nino shouldBe expectedNino
        fUnit
      }

      override def getEvents(nino: Nino): Future[List[SavingsGoalEvent]] = {
        nino shouldBe expectedNino
        goalsOrException match {
          case Left(t)     => Future.failed(t)
          case Right(goal) => Future.successful(goal)
        }
      }

      override def deleteGoal(nino: Nino): Future[Unit] = {
        nino shouldBe expectedNino
        fUnit
      }
    }

  object ShouldNotBeCalledGetAccount extends HelpToSaveGetAccount {

    override def getAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
      Future failed new RuntimeException("HelpToSaveGetAccount.getAccount should not be called in this situation")
    }
  }
}
