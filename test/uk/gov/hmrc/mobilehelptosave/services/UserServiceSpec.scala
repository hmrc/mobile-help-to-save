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

import org.scalatest.OptionValues
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.mobilehelptosave.domain.{UserDetails, UserState}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec extends UnitSpec with OptionValues {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "the state returned by userDetails" should {
    "be Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        fakeSurveyService(wantsToBeContacted = Some(false))
      )

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.Enrolled
    }

    "be Enrolled when the current user is enrolled in Help to Save, even if they have been invited" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)),
        fakeSurveyService(wantsToBeContacted = Some(true))
      )

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.Enrolled
    }

    "be NotEnrolled when the current user is not enrolled in Help to Save" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(false))
      )

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.NotEnrolled
    }

    "be InvitedFirstTime if the user is not enrolled and said they wanted to be contacted in the survey" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = Some(true))
      )

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.InvitedFirstTime
    }
  }

  "userDetails" should {
    "return no details when the HelpToSaveConnector returns None" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = None),
        fakeSurveyService(wantsToBeContacted = Some(false))
      )

      await(service.userDetails()) shouldBe None
    }

    "return no details when the SurveyService returns None" in {
      val service = new UserService(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)),
        fakeSurveyService(wantsToBeContacted = None)
      )

      await(service.userDetails()) shouldBe None
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
