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

package uk.gov.hmrc.mobilehelptosave.controllers

import org.scalatest.OptionValues
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnector
import uk.gov.hmrc.mobilehelptosave.domain.{UserDetails, UserState}
import uk.gov.hmrc.mobilehelptosave.services.UserService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class UserServiceSpec extends UnitSpec with OptionValues {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "userDetails" should {
    "return details with state=Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserService(fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(true)))

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.Enrolled
    }

    "return details with state=NotEnrolled when the current user is not enrolled in Help to Save" in {
      val service = new UserService(fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Some(false)))

      val user: UserDetails = await(service.userDetails()).value
      user.state shouldBe UserState.NotEnrolled
    }

    "return no details when the connector returns None" in {
      val service = new UserService(fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = None))

      await(service.userDetails()) shouldBe None
    }

  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Option[Boolean]) = new HelpToSaveConnector {
    override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = Future successful userIsEnrolledInHelpToSave
  }

  // disable implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}
