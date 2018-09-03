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

import org.joda.time.{LocalDate, YearMonth}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{EitherValues, OneInstancePerTest}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.UserServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec
  extends UnitSpec
    with MockFactory with OneInstancePerTest with LoggerStub
    with EitherValues {

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  private val internalAuthId = InternalAuthId("test-internal-auth-id")

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  private val testAccount = Account(
    number = "2000000000001",
    openedYearMonth = new YearMonth(2018, 5),
    isClosed = false,
    Blocking(false),
    BigDecimal("543.12"),
    0, 0, 0,
    thisMonthEndDate = new LocalDate(2020, 12, 31),
    nextPaymentMonthStartDate = None,
    accountHolderName = "",
    accountHolderEmail = None,
    bonusTerms = Seq.empty)


  private class UserServiceWithTestDefaults(
    helpToSaveConnector: HelpToSaveConnectorEnrolmentStatus,
    accountService: AccountService = fakeAccountService(nino, Left(ErrorInfo.General)),
    balanceEnabled: Boolean = true,
    paidInThisMonthEnabled: Boolean = true,
    firstBonusEnabled: Boolean = true
  ) extends UserService(
    logger,
    helpToSaveConnector,
    accountService,
    config = TestUserServiceConfig(
    balanceEnabled = balanceEnabled,
    paidInThisMonthEnabled = paidInThisMonthEnabled,
    firstBonusEnabled = firstBonusEnabled
    )
  )

  "userDetails" should {
    "return state=Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true))
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
      user.state shouldBe UserState.Enrolled
    }

    "return state=NotEnrolled when the current user is not enrolled in Help to Save" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false))
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
      user.state shouldBe UserState.NotEnrolled
    }
  }

  "userDetails" when {
    "user is enrolled in Help to Save and all account-related feature flags are enabled" should {

      val accountReturnedByAccountService = testAccount
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        accountService = fakeAccountService(nino, Right(Some(accountReturnedByAccountService)))
      )

      "return state=Enrolled" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.state shouldBe UserState.Enrolled
      }

      "include account information" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe Some(accountReturnedByAccountService)
      }
    }

    "there is an error getting account details" should {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        accountService = fakeAccountService(nino, Left(ErrorInfo.General))
      )

      "include accountError" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe None
        user.accountError shouldBe Some(ErrorInfo.General)
      }
    }

    "user is enrolled in Help to Save but no account exists in NS&I" should {

      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        accountService = fakeAccountService(nino, Right(None))
      )

      "return state=Enrolled" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.state shouldBe UserState.Enrolled
      }

      "include accountError and log a warning" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe None
        user.accountError shouldBe Some(ErrorInfo.General)
        (slf4jLoggerStub.warn(_: String)) verify s"${nino.value} was enrolled according to help-to-save microservice but no account was found in NS&I - data is inconsistent"
      }
    }

    "user is enrolled in Help to Save and some but not all account-related feature flags are enabled" should {

      val accountReturnedByAccountService = testAccount
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        accountService = fakeAccountService(nino, Right(Some(accountReturnedByAccountService))),
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = true
      )

      "include account information" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe Some(accountReturnedByAccountService)
      }
    }

    "user is enrolled in Help to Save and no account-related feature flags are enabled" should {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        accountService = shouldNotBeCalledAccountService,
        balanceEnabled = false,
        paidInThisMonthEnabled = false,
        firstBonusEnabled = false
      )

      "not call accountService" in {
        // lack of call to accountService is checked by use of shouldNotBeCalledAccountService when constructing UserService
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe None
      }
    }
  }

  "userDetails" should {

    "return an error when the HelpToSaveConnector return an error" in {
      val error = ErrorInfo.General
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Left(error))
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe Left(error)
    }
  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Either[ErrorInfo, Boolean]) = new HelpToSaveConnectorEnrolmentStatus {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful userIsEnrolledInHelpToSave
    }
  }

  private def fakeAccountService(expectedNino: Nino, accountToReturn: Either[ErrorInfo, Option[Account]]): AccountService = new AccountService {
    override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful accountToReturn
    }
  }

  private lazy val shouldNotBeCalledAccountService = new AccountService {
    override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
      Future failed new RuntimeException("AccountService should not be called in this situation")
  }

  // disable implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}

private case class TestUserServiceConfig(
  balanceEnabled: Boolean,
  paidInThisMonthEnabled: Boolean,
  firstBonusEnabled: Boolean
) extends UserServiceConfig
