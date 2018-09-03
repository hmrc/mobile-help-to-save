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

import org.joda.time.{DateTime, LocalDate, ReadableInstant, YearMonth}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{EitherValues, OneInstancePerTest}
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.UserServiceConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.metrics.{FakeMobileHelpToSaveMetrics, MobileHelpToSaveMetrics, ShouldNotUpdateInvitationMetrics}
import uk.gov.hmrc.mobilehelptosave.repos.{FakeInvitationRepository, InvitationRepository}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.GenTraversable
import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec
  extends UnitSpec
    with MockFactory with OneInstancePerTest with LoggerStub
    with EitherValues {

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  private val internalAuthId = InternalAuthId("test-internal-auth-id")
  private val fixedClock = new FixedFakeClock(DateTime.parse("2017-11-22T10:20:30"))

  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val nino1 = generator.nextNino
  private val nino2 = generator.nextNino
  private val nino3 = generator.nextNino
  private val nino4 = generator.nextNino
  private val allTestNinos = Seq(nino, nino1, nino2, nino3, nino4)

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
    invitationEligibilityService: InvitationEligibilityService,
    helpToSaveConnector: HelpToSaveConnectorEnrolmentStatus,
    metrics: MobileHelpToSaveMetrics,
    invitationRepository: InvitationRepository,
    accountService: AccountService = fakeAccountService(nino, Left(ErrorInfo.General)),
    clock: Clock = fixedClock,
    dailyInvitationCap: Int = 1000,
    balanceEnabled: Boolean = true,
    paidInThisMonthEnabled: Boolean = true,
    firstBonusEnabled: Boolean = true
  ) extends UserService(
    logger,
    invitationEligibilityService,
    helpToSaveConnector,
    metrics,
    invitationRepository,
    accountService,
    clock,
    config = TestUserServiceConfig(
    dailyInvitationCap = dailyInvitationCap,
    balanceEnabled = balanceEnabled,
    paidInThisMonthEnabled = paidInThisMonthEnabled,
    firstBonusEnabled = firstBonusEnabled
    )
  )

  "userDetails" should {
    "return state=Enrolled when the current user is enrolled in Help to Save, even if they are eligible to be invited" in {
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Right(true)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
      user.state shouldBe UserState.Enrolled
    }

    "return state=Enrolled when the current user is enrolled in Help to Save, even if they have been invited" in {
      val repository = new FakeInvitationRepository
      await(repository.insert(Invitation(internalAuthId, fixedClock.now().minusDays(1))))

      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Right(true)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        repository
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
      user.state shouldBe UserState.Enrolled
    }
  }

  "userDetails" when {
    "user is enrolled in Help to Save and all account-related feature flags are enabled" should {

      val accountReturnedByAccountService = testAccount
      val service = new UserServiceWithTestDefaults(
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
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
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
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
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
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
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
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
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
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

    "user is enrolled in Help to Save, the flags are set, and the daily cap limit has been reached" should {
      val accountReturnedByAccountService = testAccount

      val service = new UserServiceWithTestDefaults(
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
        accountService = fakeAccountService(nino, Right(Some(accountReturnedByAccountService))),
        balanceEnabled = true,
        paidInThisMonthEnabled = true,
        firstBonusEnabled = true,
        dailyInvitationCap = 0
      )

      "not perform the eligibility check" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.account shouldBe Some(accountReturnedByAccountService)
        user.state shouldBe UserState.Enrolled
      }
    }

    "user is eligible to be invited" should {

      val invitationEligibilityService = fakeInvitationEligibilityService(allTestNinos, eligible = Right(true))

      "return state=InvitedFirstTime (invite the user), store the time of the invitation and increment the counter " +
      "if the user is not enrolled in Help to Save" in {
        val metrics = FakeMobileHelpToSaveMetrics()
        val invitationRepo = new FakeInvitationRepository

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo
        )

        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.state shouldBe UserState.InvitedFirstTime

        await(invitationRepo.findById(internalAuthId)).value.created shouldBe fixedClock.now()

        metrics.invitationCounter.getCount shouldBe 1
      }

      "change from InvitedFirstTime to Invited the second time it is checked (but retain the same time)" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          new FakeInvitationRepository
        )

        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.Invited
        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.Invited

        metrics.invitationCounter.getCount shouldBe 1
      }

      "return state=Invited in the unlikely event a not enrolled user accesses the system from two devices at almost exactly the same time" in {
        val stubRepo = stub[InvitationRepository]

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          ShouldNotUpdateInvitationMetrics,
          stubRepo
        )

        // The test scenario here is that the user's other session inserts an
        // invitation in between this session calling findById() and this session
        // calling insert() - i.e. a race condition.
        (stubRepo.findById(_: InternalAuthId, _: ReadPreference)(_: ExecutionContext))
          .when(internalAuthId, *, *)
          .returns(Future successful None)

        val duplicateKeyException = GenericDatabaseException("already exists", Some(11000))
        (stubRepo.insert(_: Invitation)(_: ExecutionContext))
          .when(argThat((i: Invitation) => i.internalAuthId == internalAuthId), *)
          .returns(Future failed duplicateKeyException)

        stubRepo.isDuplicateKey _ when duplicateKeyException returns true

        (stubRepo.countCreatedSince(_: DateTime)(_: ExecutionContext))
          .when(*, *)
          .returns(Future successful 0)

        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.Invited
      }

      "not change state from NotEnrolled to InvitedFirstTime when the daily cap has been reached" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 3
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-3"), nino3)).right.value.state shouldBe UserState.InvitedFirstTime
        val capExceededInternalAuthId = InternalAuthId("test-internal-auth-id-4")
        await(service.userDetails(capExceededInternalAuthId, nino4)).right.value.state shouldBe UserState.NotEnrolled

        await(invitationRepo.findById(capExceededInternalAuthId)) shouldBe None

        metrics.invitationCounter.getCount shouldBe 3
      }

      "ensure that the eligibility check is not performed once the daily cap has been reached" in {

        val metrics = FakeMobileHelpToSaveMetrics()
        val eligibilityService = mock[InvitationEligibilityService]

        (eligibilityService.userIsEligibleToBeInvited(_: Nino)( _: HeaderCarrier, _: ExecutionContext)).expects(nino1,*,*).returns(Future successful Right(true))
        (eligibilityService.userIsEligibleToBeInvited(_: Nino)( _: HeaderCarrier, _: ExecutionContext)).expects(nino2,*,*).never()

        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          eligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 1
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).right.value.state shouldBe UserState.NotEnrolled

        metrics.invitationCounter.getCount shouldBe 1
      }

      "ensure the eligibility check is never called if the cap is set to 0" in {

        val metrics = FakeMobileHelpToSaveMetrics()

        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          shouldNotBeCalledInvitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 0
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1))

        metrics.invitationCounter.getCount shouldBe 0
      }

      "continue to return Invited for already-invited users even when the cap has been reached"  in {
        val metrics = FakeMobileHelpToSaveMetrics()


        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 3
        )

        // fill up the cap
        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).right.value.state shouldBe UserState.InvitedFirstTime
        val successfullyInvitedInternalAuthid = InternalAuthId("test-internal-auth-id-3")
        val successfullyInvitedNino = nino3
        await(service.userDetails(successfullyInvitedInternalAuthid, successfullyInvitedNino)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-4"), nino4)).right.value.state shouldBe UserState.NotEnrolled

        // check an already-invited user's status again
        await(service.userDetails(successfullyInvitedInternalAuthid, successfullyInvitedNino)).right.value.state shouldBe UserState.Invited

        metrics.invitationCounter.getCount shouldBe 3
      }

      "only count invitations made today towards the cap" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val clock = new VariableFakeClock(DateTime.parse("2017-11-22T10:20:30"))
        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          invitationRepo,
          clock = clock,
          dailyInvitationCap = 3
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).right.value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-3"), nino3)).right.value.state shouldBe UserState.InvitedFirstTime
        val capExceededInternalAuthId = InternalAuthId("test-internal-auth-id-4")
        val capExceededNino = nino4
        await(service.userDetails(capExceededInternalAuthId, capExceededNino)).right.value.state shouldBe UserState.NotEnrolled

        clock.time = clock.time.plusDays(1)
        await(service.userDetails(capExceededInternalAuthId, capExceededNino)).right.value.state shouldBe UserState.InvitedFirstTime

        metrics.invitationCounter.getCount shouldBe 4
      }

      "use UTC timezone when counting invitations made today for the cap" in {
        val clock = new VariableFakeClock(DateTime.parse("2017-06-01T10:20:30+01:00"))
        val mockRepo = mock[InvitationRepository]
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          FakeMobileHelpToSaveMetrics(),
          mockRepo,
          clock = clock,
          dailyInvitationCap = 3
        )

        val internalAuthId = InternalAuthId("test-internal-auth-id-1")
        (mockRepo.findById(_: InternalAuthId, _: ReadPreference)(_: ExecutionContext))
          .expects(internalAuthId, *, *)
          .anyNumberOfTimes()
          .returning(Future successful None)
        (mockRepo.insert(_: Invitation)(_: ExecutionContext))
          .expects(*, *)
          .anyNumberOfTimes()
          .returning(Future successful DefaultWriteResult(ok = true, n = 1, Seq.empty, None, None, None))

        val midnightUtc = DateTime.parse("2017-06-01T00:00:00Z")
        val midnightBst = DateTime.parse("2017-06-01T00:00:00+01:00")

        def sameInstant(other: ReadableInstant) =
          argThat((i: ReadableInstant) => i.isEqual(other))

        // These 2 mock expectations are the important part of the test:
        // countCreatedSince should be called with midnight UTC, not midnight BST
        (mockRepo.countCreatedSince(_: DateTime)(_: ExecutionContext))
          .expects(sameInstant(midnightUtc), *)
          .returning(Future successful 0)

        (mockRepo.countCreatedSince(_: DateTime)(_: ExecutionContext))
          .expects(sameInstant(midnightBst), *)
          .never()

        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.InvitedFirstTime
      }

      "return state=InvitedFirstTime even when a different user has already been invited" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          metrics,
          new FakeInvitationRepository
        )

        await(service.userDetails(internalAuthId, nino)).right.value.state shouldBe UserState.InvitedFirstTime
        val otherInternalAuthId = InternalAuthId("other-test-internal-auth-id")
        await(service.userDetails(otherInternalAuthId, nino2)).right.value.state shouldBe UserState.InvitedFirstTime

        metrics.invitationCounter.getCount shouldBe 2
      }
    }

    "user is not eligible to be invited" should {

      val invitationEligibilityService = fakeInvitationEligibilityService(nino, eligible = Right(false))

      "return state=NotEnrolled (not invite the user) " +
      "if the user is not enrolled in Help to Save" in {
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
          ShouldNotUpdateInvitationMetrics,
          new FakeInvitationRepository
        )

        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).right.value
        user.state shouldBe UserState.NotEnrolled
      }
    }
  }

  "userDetails" should {

    "return an error when the HelpToSaveConnector return an error" in {
      val error = ErrorInfo.General
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Right(false)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Left(error)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe Left(error)
    }

    "return an error when the InvitationEligibilityService returns an error" in {
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Left(ErrorInfo.General)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe Left(ErrorInfo.General)
    }

  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Either[ErrorInfo, Boolean]) = new HelpToSaveConnectorEnrolmentStatus {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful userIsEnrolledInHelpToSave
    }
  }

  private def fakeInvitationEligibilityService(expectedNino: Nino, eligible: Either[ErrorInfo, Boolean]): InvitationEligibilityService =
    fakeInvitationEligibilityService(Seq(expectedNino), eligible)

  private def fakeInvitationEligibilityService(expectedNinos: GenTraversable[Nino], eligible: Either[ErrorInfo, Boolean]) = new InvitationEligibilityService {
    override def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
      expectedNinos should contain (nino)
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful eligible
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

  private lazy val shouldNotBeCalledHelpToSaveConnector = new HelpToSaveConnectorEnrolmentStatus {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] =
      Future failed new RuntimeException("HelpToSaveConnector should not be called in this situation")
  }

  private lazy val shouldNotBeCalledInvitationEligibilityService = new InvitationEligibilityService {
    override def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] =
      Future failed new RuntimeException("InvitationEligibilityService should not be called in this situation")
  }

  private lazy val shouldNotBeCalledAccountService = new AccountService {
    override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Option[Account]]] =
      Future failed new RuntimeException("AccountService should not be called in this situation")
  }

  // disable implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}

private case class TestUserServiceConfig(
  dailyInvitationCap: Int,
  balanceEnabled: Boolean,
  paidInThisMonthEnabled: Boolean,
  firstBonusEnabled: Boolean
) extends UserServiceConfig
