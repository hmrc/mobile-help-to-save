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

import org.joda.time.{DateTime, ReadableInstant}
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.metrics.{FakeMobileHelpToSaveMetrics, MobileHelpToSaveMetrics, ShouldNotUpdateInvitationMetrics}
import uk.gov.hmrc.mobilehelptosave.repos.{FakeInvitationRepository, InvitationRepository, ShouldNotBeCalledInvitationRepository}
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.GenTraversable
import scala.concurrent.ExecutionContext.Implicits.{global => passedEc}
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec extends UnitSpec with MockFactory with OptionValues {

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

  private class UserServiceWithTestDefaults(
    invitationEligibilityService: InvitationEligibilityService,
    helpToSaveConnector: HelpToSaveConnector,
    metrics: MobileHelpToSaveMetrics,
    invitationRepository: InvitationRepository,
    accountService: AccountService = fakeAccountService(nino, None),
    clock: Clock = fixedClock,
    enabled: Boolean = true,
    dailyInvitationCap: Int = 1000
  ) extends UserService(
    invitationEligibilityService,
    helpToSaveConnector,
    metrics,
    invitationRepository,
    accountService,
    clock,
    enabled = enabled,
    dailyInvitationCap = dailyInvitationCap
  )

  "userDetails" should {
    "return None and not call the connector when Help to Save is not enabled" in {
      val service = new UserServiceWithTestDefaults(
        shouldNotBeCalledInvitationEligibilityService,
        shouldNotBeCalledHelpToSaveConnector,
        ShouldNotUpdateInvitationMetrics,
        ShouldNotBeCalledInvitationRepository,
        enabled = false
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe None
    }

    "return state=Enrolled when the current user is enrolled in Help to Save, even if they are eligible to be invited" in {
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Some(true)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
      user.state shouldBe UserState.Enrolled
    }

    "return state=Enrolled when the current user is enrolled in Help to Save, even if they have been invited" in {
      val repository = new FakeInvitationRepository
      await(repository.insert(Invitation(internalAuthId, fixedClock.now().minusDays(1))))

      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Some(true)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        ShouldNotUpdateInvitationMetrics,
        repository
      )

      val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
      user.state shouldBe UserState.Enrolled
    }
  }

  "userDetails" when {
    "user is enrolled in Help to Save" should {

      val accountReturnedByAccountService = Account(BigDecimal("543.12"), bonusTerms = Seq.empty)
      val service = new UserServiceWithTestDefaults(
        shouldNotBeCalledInvitationEligibilityService,
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository,
        accountService = fakeAccountService(nino, Some(accountReturnedByAccountService))
      )

      "return state=Enrolled" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
        user.state shouldBe UserState.Enrolled
      }

      "include account information" in {
        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
        user.account shouldBe Some(accountReturnedByAccountService)
      }

    }

    "user is eligible to be invited" should {

      val invitationEligibilityService = fakeInvitationEligibilityService(allTestNinos, eligible = Some(true))

      "return state=InvitedFirstTime (invite the user), store the time of the invitation and increment the counter " +
      "if the user is not enrolled in Help to Save" in {
        val metrics = FakeMobileHelpToSaveMetrics()
        val invitationRepo = new FakeInvitationRepository

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          invitationRepo
        )

        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
        user.state shouldBe UserState.InvitedFirstTime

        await(invitationRepo.findById(internalAuthId)).value.created shouldBe fixedClock.now()

        metrics.invitationCounter.getCount shouldBe 1
      }

      "change from InvitedFirstTime to Invited the second time it is checked (but retain the same time)" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          new FakeInvitationRepository
        )

        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.Invited
        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.Invited

        metrics.invitationCounter.getCount shouldBe 1
      }

      "return state=Invited in the unlikely event a not enrolled user accesses the system from two devices at almost exactly the same time" in {
        val stubRepo = stub[InvitationRepository]

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
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

        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.Invited
      }

      "not change state from NotEnrolled to InvitedFirstTime when the daily cap has been reached" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 3
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-3"), nino3)).value.state shouldBe UserState.InvitedFirstTime
        val capExceededInternalAuthId = InternalAuthId("test-internal-auth-id-4")
        await(service.userDetails(capExceededInternalAuthId, nino4)).value.state shouldBe UserState.NotEnrolled

        await(invitationRepo.findById(capExceededInternalAuthId)) shouldBe None

        metrics.invitationCounter.getCount shouldBe 3
      }

      "continue to return Invited for already-invited users even when the cap has been reached"  in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          invitationRepo,
          dailyInvitationCap = 3
        )

        // fill up the cap
        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).value.state shouldBe UserState.InvitedFirstTime
        val successfullyInvitedInternalAuthid = InternalAuthId("test-internal-auth-id-3")
        val successfullyInvitedNino = nino3
        await(service.userDetails(successfullyInvitedInternalAuthid, successfullyInvitedNino)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-4"), nino4)).value.state shouldBe UserState.NotEnrolled

        // check an already-invited user's status again
        await(service.userDetails(successfullyInvitedInternalAuthid, successfullyInvitedNino)).value.state shouldBe UserState.Invited

        metrics.invitationCounter.getCount shouldBe 3
      }

      "only count invitations made today towards the cap" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val clock = new VariableFakeClock(DateTime.parse("2017-11-22T10:20:30"))
        val invitationRepo = new FakeInvitationRepository
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          invitationRepo,
          clock = clock,
          dailyInvitationCap = 3
        )

        await(service.userDetails(InternalAuthId("test-internal-auth-id-1"), nino1)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-2"), nino2)).value.state shouldBe UserState.InvitedFirstTime
        await(service.userDetails(InternalAuthId("test-internal-auth-id-3"), nino3)).value.state shouldBe UserState.InvitedFirstTime
        val capExceededInternalAuthId = InternalAuthId("test-internal-auth-id-4")
        val capExceededNino = nino4
        await(service.userDetails(capExceededInternalAuthId, capExceededNino)).value.state shouldBe UserState.NotEnrolled

        clock.time = clock.time.plusDays(1)
        await(service.userDetails(capExceededInternalAuthId, capExceededNino)).value.state shouldBe UserState.InvitedFirstTime

        metrics.invitationCounter.getCount shouldBe 4
      }

      "use UTC timezone when counting invitations made today for the cap" in {
        val clock = new VariableFakeClock(DateTime.parse("2017-06-01T10:20:30+01:00"))
        val mockRepo = mock[InvitationRepository]
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
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

        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.InvitedFirstTime
      }

      "return state=InvitedFirstTime even when a different user has already been invited" in {
        val metrics = FakeMobileHelpToSaveMetrics()

        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          metrics,
          new FakeInvitationRepository
        )

        await(service.userDetails(internalAuthId, nino)).value.state shouldBe UserState.InvitedFirstTime
        val otherInternalAuthId = InternalAuthId("other-test-internal-auth-id")
        await(service.userDetails(otherInternalAuthId, nino2)).value.state shouldBe UserState.InvitedFirstTime

        metrics.invitationCounter.getCount shouldBe 2
      }
    }

    "user is not eligible to be invited" should {

      val invitationEligibilityService = fakeInvitationEligibilityService(nino, eligible = Some(false))

      "return state=NotEnrolled (not invite the user) " +
      "if the user is not enrolled in Help to Save" in {
        val service = new UserServiceWithTestDefaults(
          invitationEligibilityService,
          fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
          ShouldNotUpdateInvitationMetrics,
          new FakeInvitationRepository
        )

        val user: UserDetails = await(service.userDetails(internalAuthId, nino)).value
        user.state shouldBe UserState.NotEnrolled
      }
    }
  }

  "userDetails" should {

    "return no details when the HelpToSaveConnector returns None" in {
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = Some(false)),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = None),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe None
    }

    "return no details when the InvitationEligibilityService returns None" in {
      val service = new UserServiceWithTestDefaults(
        fakeInvitationEligibilityService(nino, eligible = None),
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        ShouldNotUpdateInvitationMetrics,
        new FakeInvitationRepository
      )

      await(service.userDetails(internalAuthId, nino)) shouldBe None
    }

  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Option[Boolean]) = new HelpToSaveConnector {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful userIsEnrolledInHelpToSave
    }
  }

  private def fakeInvitationEligibilityService(expectedNino: Nino, eligible: Option[Boolean]): InvitationEligibilityService =
    fakeInvitationEligibilityService(Seq(expectedNino), eligible)

  private def fakeInvitationEligibilityService(expectedNinos: GenTraversable[Nino], eligible: Option[Boolean]) = new InvitationEligibilityService {
    override def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
      expectedNinos should contain (nino)
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful eligible
    }
  }

  private def fakeAccountService(expectedNino: Nino, accountToReturn: Option[Account]): AccountService = new AccountService {
    override def account(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Account]] = {
      nino shouldBe expectedNino
      hc shouldBe passedHc
      ec shouldBe passedEc

      Future successful accountToReturn
    }
  }

  private val shouldNotBeCalledHelpToSaveConnector = new HelpToSaveConnector {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
      Future failed new RuntimeException("HelpToSaveConnector should not be called in this situation")
  }

  private val shouldNotBeCalledInvitationEligibilityService = new InvitationEligibilityService {
    override def userIsEligibleToBeInvited(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
      Future failed new RuntimeException("InvitationEligibilityService should not be called in this situation")
  }

  // disable implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
