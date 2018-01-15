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

import org.joda.time.DateTime
import org.scalatest.OptionValues
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, UserDetails, UserState}
import uk.gov.hmrc.mobilehelptosave.repos.FakeInvitationRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec extends UnitSpec with OptionValues {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val internalAuthId = InternalAuthId("test-internal-auth-id")
  private val fakeNow = DateTime.parse("2017-11-22T10:20:30")
  private val fakeClock = new FakeClock(fakeNow)

  "userDetails" should {
    "return state=Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        fakeSurveyService(wantsToBeContacted = Some(false)),
        new FakeInvitationRepository,
        fakeClock
      )

      val user: UserDetails = await(service.userDetails(internalAuthId)).value
      user.state shouldBe UserState.Enrolled
    }

    "return state=Enrolled when the current user is enrolled in Help to Save, even if they have been invited" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        fakeSurveyService(wantsToBeContacted = Some(true)),
        new FakeInvitationRepository,
        fakeClock
      )

      val user: UserDetails = await(service.userDetails(internalAuthId)).value
      user.state shouldBe UserState.Enrolled
    }

    "return state=NotEnrolled when the current user is not enrolled in Help to Save" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(false)),
        new FakeInvitationRepository,
        fakeClock
      )

      val user: UserDetails = await(service.userDetails(internalAuthId)).value
      user.state shouldBe UserState.NotEnrolled
    }

    "return state=InvitedFirstTime and store the time of the invitation if the user is not enrolled and said they wanted to be contacted in the survey" in {
      val invitationRepo = new FakeInvitationRepository
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(true)),
        invitationRepo,
        fakeClock
      )

      val user: UserDetails = await(service.userDetails(internalAuthId)).value
      user.state shouldBe UserState.InvitedFirstTime

      await(invitationRepo.findById(internalAuthId)).value.created shouldBe fakeNow
    }

    "change from InvitedFirstTime to Invited the second time it is checked (but retain the same time)" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(true)),
        new FakeInvitationRepository,
        fakeClock
      )

      await(service.userDetails(internalAuthId)).value.state shouldBe UserState.InvitedFirstTime
      await(service.userDetails(internalAuthId)).value.state shouldBe UserState.Invited
      await(service.userDetails(internalAuthId)).value.state shouldBe UserState.Invited
    }

    "return state=InvitedFirstTime even when a different user has already been invited" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(true)),
        new FakeInvitationRepository,
        fakeClock
      )

      await(service.userDetails(internalAuthId)).value.state shouldBe UserState.InvitedFirstTime
      val otherInternalAuthId = InternalAuthId("other-test-internal-auth-id")
      await(service.userDetails(otherInternalAuthId)).value.state shouldBe UserState.InvitedFirstTime
    }

    "return no details when the HelpToSaveConnector returns None" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = None),
        fakeSurveyService(wantsToBeContacted = Some(false)),
        new FakeInvitationRepository,
        fakeClock
      )

      await(service.userDetails(internalAuthId)) shouldBe None
    }

    "return no details when the SurveyService returns None" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = None),
        new FakeInvitationRepository,
        fakeClock
      )

      await(service.userDetails(internalAuthId)) shouldBe None
    }

  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Option[Boolean]) = new HelpToSaveConnector {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future successful userIsEnrolledInHelpToSave
  }

  private def fakeSurveyService(wantsToBeContacted: Option[Boolean]) = new SurveyService {
    override def userWantsToBeContacted()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future successful wantsToBeContacted
  }

  // disable implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
