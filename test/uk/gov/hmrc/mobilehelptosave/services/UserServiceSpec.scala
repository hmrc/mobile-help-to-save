/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.EitherValues
import play.api.LoggerLike
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.{HelpToSaveEligibility, HelpToSaveEnrolmentStatus}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.EligibilityRepo
import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserServiceSpec extends BaseSpec with EitherValues {

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()
  val logger = mock[LoggerLike]
  private val testConfig = TestUserServiceConfig(eligibilityCheckEnabled = true)

  private class UserServiceWithTestDefaults(
    helpToSaveEnrolmentStatus: HelpToSaveEnrolmentStatus,
    helpToSaveEligibility:     HelpToSaveEligibility,
    eligibilityStatusRepo:     EligibilityRepo)
      extends HtsUserService(
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

      val user: UserDetails = await(service.userDetails(nino)).value
      user.state mustBe UserState.Enrolled
    }

    "return state=NotEnrolled when the current user is not enrolled in Help to Save and not eligible" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(notEligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).value
      user.state mustBe UserState.NotEnrolled
    }

    "return state=NotEnrolledButEligible when the current user is not enrolled in Help to Save but is eligible" in {
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(eligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).value
      user.state mustBe UserState.NotEnrolledButEligible
    }

    "allow eligibilityCheckEnabled to be overridden in configuration" in {
      val service = new HtsUserService(
        logger,
        testConfig.copy(eligibilityCheckEnabled                  = false),
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Right(false)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Right(eligible)),
        fakeEligibilityRepo(None)
      )

      val user: UserDetails = await(service.userDetails(nino)).value
      user.state mustBe UserState.NotEnrolled
    }

    "return an error when the HelpToSaveConnector returns an error" in {
      val error = ErrorInfo.General
      val service = new UserServiceWithTestDefaults(
        fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave = Left(error)),
        fakeHelpToSaveEligibility(userIsEligibleForHelpToSave    = Left(error)),
        fakeEligibilityRepo(None)
      )

      await(service.userDetails(nino))  mustBe Left(error)
    }
  }

  private def fakeHelpToSaveEnrolmentStatus(userIsEnrolledInHelpToSave: Either[ErrorInfo, Boolean]) =
    new HelpToSaveEnrolmentStatus {

      override def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] = {
        hc mustBe passedHc

        Future successful userIsEnrolledInHelpToSave
      }
    }

  private def fakeHelpToSaveEligibility(userIsEligibleForHelpToSave: Either[ErrorInfo, EligibilityCheckResponse]) =
    new HelpToSaveEligibility {

      override def checkEligibility(
      )(implicit hc: HeaderCarrier
      ): Future[Either[ErrorInfo, EligibilityCheckResponse]] = {
        hc mustBe passedHc

        Future successful userIsEligibleForHelpToSave
      }
    }

  private def fakeEligibilityRepo(eligibility: Option[Eligibility]) =
    new EligibilityRepo {
      override def setEligibility(eligibility: Eligibility): Future[Unit]                = Future.successful(())
      override def getEligibility(nino:        Nino):        Future[Option[Eligibility]] = Future.successful(eligibility)
    }

}
