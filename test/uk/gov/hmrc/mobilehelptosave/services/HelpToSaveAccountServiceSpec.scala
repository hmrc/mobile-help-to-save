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
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.AccountTestData
import uk.gov.hmrc.mobilehelptosave.config.AccountServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveAccount, HelpToSaveEnrolmentStatus, HelpToSaveGetAccount}
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalMongoModel, SavingsGoalRepo}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveAccountServiceSpec
  extends WordSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with FutureAwaits
    with DefaultAwaitTimeout
    with AccountTestData
    with MockFactory
    with OneInstancePerTest
    with LoggerStub {

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestAccountServiceConfig(inAppPaymentsEnabled = false, savingsGoalsEnabled = false)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  "account" should {
    "convert the account from the help-to-save domain to the mobile-help-to-save domain" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    "fold the value of the 'savingsGoalEnabled' config into the returned account" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))

      forAll { enabled: Boolean =>
        val config = testConfig.copy(savingsGoalsEnabled = enabled)
        val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, config, fakeGoalRepo)

        await(service.account(nino)) shouldBe Right(Some(mobileHelpToSaveAccount.copy(savingsGoalsEnabled = enabled)))
      }
    }

    "allow inAppPaymentsEnabled to be overridden with configuration" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig.copy(inAppPaymentsEnabled = true), fakeGoalRepo)
      await(service.account(nino)) shouldBe Right(Some(mobileHelpToSaveAccount.copy(inAppPaymentsEnabled = true, savingsGoalsEnabled = testConfig.savingsGoalsEnabled)))
    }

    // this is to avoid unnecessary load on NS&I, see NGC-3799
    "return None without attempting to get account from help-to-save when the user is not enrolled" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(false))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, ShouldNotBeCalledGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Right(None)

      (slf4jLoggerStub.warn(_: String)) verify * never()
    }

    "return None and log a warning when user is enrolled according to help-to-save but no account exists in NS&I" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(None))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Right(None)

      (slf4jLoggerStub.warn(_: String)) verify s"${nino.value} was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
    }

    "not call either fetchSavingsGoal or fetchNSAndIAccount if the user isn't enrolled" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(false))

      val fakeGetAccount = new HelpToSaveGetAccount {
        override def getAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] =
          fail("getAccount should not have been called")
      }

      val fakeGoalRepo = new SavingsGoalRepo {
        override def setGoal(nino: Nino, amount: Double): Future[Unit] = fail("setGoal should not have been called")
        override def get(nino: Nino): Future[Option[SavingsGoalMongoModel]] = fail("get should not have been called")
        override def delete(nino: Nino): Future[Unit] = fail("delete should not have been called")
      }

      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)

      await(service.account(nino)) shouldBe Right(None)
    }

    "return errors returned by connector.enrolmentStatus" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Left(ErrorInfo.General))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Left(ErrorInfo.General)
    }

    "return errors returned by connector.getAccount" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Left(ErrorInfo.General))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Right(None))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Left(ErrorInfo.General)
    }

    "return errors if savingsGoalRepo.get throws exception" in {
      val fakeEnrolmentStatus = fakeHelpToSaveEnrolmentStatus(nino, Right(true))
      val fakeGetAccount = fakeHelpToSaveGetAccount(nino, Right(Some(helpToSaveAccount)))
      val fakeGoalRepo = fakeSavingsGoalRepo(nino, Left(new Exception("test exception")))
      val service = new HelpToSaveAccountService(logger, fakeEnrolmentStatus, fakeGetAccount, testConfig, fakeGoalRepo)
      await(service.account(nino)) shouldBe Left(ErrorInfo.General)
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

  private def fakeSavingsGoalRepo(expectedNino: Nino, goalOrException: Either[Throwable, Option[SavingsGoalMongoModel]]): SavingsGoalRepo = new SavingsGoalRepo {
    override def setGoal(nino: Nino, amount: Double): Future[Unit] = {
      nino shouldBe expectedNino
      fUnit
    }

    override def get(nino: Nino): Future[Option[SavingsGoalMongoModel]] = {
      nino shouldBe expectedNino
      goalOrException match {
        case Left(t)     => Future.failed(t)
        case Right(goal) => Future.successful(goal)
      }
    }

    override def delete(nino: Nino): Future[Unit] = {
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

case class TestAccountServiceConfig(inAppPaymentsEnabled: Boolean, savingsGoalsEnabled: Boolean) extends AccountServiceConfig
