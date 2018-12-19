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
import org.scalatest.{EitherValues, Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveEnrolmentStatus
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserServiceSpec
  extends WordSpec with Matchers
    with FutureAwaits with DefaultAwaitTimeout
    with MockFactory with OneInstancePerTest with LoggerStub
    with EitherValues {

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino      = generator.nextNino


  private class UserServiceWithTestDefaults(
    helpToSaveConnector: HelpToSaveEnrolmentStatus
  ) extends UserService(
    logger,
    helpToSaveConnector
  )

  "userDetails" should {
    "return state=Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(true))
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.Enrolled
    }

    "return state=NotEnrolled when the current user is not enrolled in Help to Save" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Right(false))
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.NotEnrolled
    }

    "return an error when the HelpToSaveConnector return an error" in {
      val error = ErrorInfo.General
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveConnector(userIsEnrolledInHelpToSave = Left(error))
      )

      await(service.userDetails(nino)) shouldBe Left(error)
    }
  }

  private def fakeHelpToSaveConnector(userIsEnrolledInHelpToSave: Either[ErrorInfo, Boolean]) = new HelpToSaveEnrolmentStatus {
    override def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] = {
      hc shouldBe passedHc

      Future successful userIsEnrolledInHelpToSave
    }
  }
}
