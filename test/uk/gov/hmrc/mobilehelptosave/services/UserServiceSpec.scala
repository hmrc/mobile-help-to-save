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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{EitherValues, Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEligibility, HelpToSaveEnrolmentStatus}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.EligibilityRepo
import uk.gov.hmrc.mobilehelptosave.support.{LoggerStub, TestF}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserServiceSpec
    extends WordSpec
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with EitherValues {

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  private val generator  = new Generator(0)
  private val nino       = generator.nextNino
  private val testConfig = TestUserServiceConfig(eligibilityCheckEnabled = true)

  private class UserServiceWithTestDefaults(
    helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus[Future],
    helpToSaveEligibility:     HelpToSaveEligibility[Future],
    eligibilityStatusRepo:     EligibilityRepo[Future]
  ) extends HtsUserService(
        logger,
        testConfig,
        helpToSaveEnrolmentStatus,
        helpToSaveEligibility,
        eligibilityStatusRepo
      )

  "userDetails" should {
    val eligible = EligibilityCheckResponse(
      EligibilityCheckResult(
        result     = "",
        resultCode = 1,
        reason     = "",
        reasonCode = 6
      ),
      threshold = None
    )

    val notEligible = EligibilityCheckResponse(
      EligibilityCheckResult(
        result     = "",
        resultCode = 2,
        reason     = "",
        reasonCode = 10
      ),
      threshold = None
    )

    "return state=Enrolled when the current user is enrolled in Help to Save" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(true)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(eligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.Enrolled
    }

    "return state=NotEnrolled when the current user is not enrolled in Help to Save and not eligible" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(notEligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.NotEnrolled
    }

    "return state=NotEnrolledButEligible when the current user is not enrolled in Help to Save but is eligible" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(eligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.NotEnrolledButEligible
    }

    "allow eligibilityCheckEnabled to be overridden in configuration" in {
      val service = new HtsUserService(
        logger,
        testConfig.copy(eligibilityCheckEnabled                  = false),
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(eligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).right.value
      user.state shouldBe UserState.NotEnrolled
    }

    "return an error when the HelpToSaveConnector returns an error" in {
      val error = ErrorInfo.General
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Left(error)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Left(error)),
        fakeEligibilityRepo(None)
      )

      await(service.userDetails(nino)) shouldBe Left(error)
    }
  }

  private def fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave: Either[ErrorInfo, Boolean]) =
    new HelpToSaveEnrolmentStatus[Future] {
      override def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] = {
        hc shouldBe passedHc

        Future successful userIsEnrolledInHelpToSave
      }
    }

  private def fakeHelpToSaveEligibility(userIsEligibleForHelpToSave: Either[ErrorInfo, EligibilityCheckResponse]) =
    new HelpToSaveEligibility[Future] {
      override def checkEligibility()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, EligibilityCheckResponse]] = {
        hc shouldBe passedHc

        Future successful userIsEligibleForHelpToSave
      }
    }

  private def fakeEligibilityRepo(eligibility: Option[Eligibility]) =
    new EligibilityRepo[Future] {
      override def setEligibility(eligibility: Eligibility): Future[Unit]                = Future.successful(())
      override def getEligibility(nino:        Nino):        Future[Option[Eligibility]] = Future.successful(eligibility)
    }

}
