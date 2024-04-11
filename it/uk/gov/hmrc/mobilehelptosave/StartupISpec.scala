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

package uk.gov.hmrc.mobilehelptosave

import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
import uk.gov.hmrc.mobilehelptosave.support.{BaseISpec, ComponentSupport}

class StartupISpec extends BaseISpec with ComponentSupport with NumberVerification {

  "GET /mobile-help-to-save/startup" should {

    "include user.state" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.currentUserIsEligible()

      val response = await(requestWithAuthHeaders("/mobile-help-to-save/startup").get())
      response.status                                  shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
    }

    "omit user state if call to help-to-save fails" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.enrolmentStatusReturnsInternalServerError()

      val response = await(requestWithAuthHeaders("/mobile-help-to-save/startup").get())
      response.status                                   shouldBe 200
      (response.json \ "user" \ "state").asOpt[String]  shouldBe None
      (response.json \ "userError" \ "code").as[String] shouldBe "GENERAL"
      // check that only the user field has been omitted, not all fields
      (response.json \ "infoUrl").asOpt[String] should not be None
    }

    "return 401 when the user is not logged in" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsNotLoggedIn()
      val response = await(requestWithAuthHeaders("/mobile-help-to-save/startup").get())
      response.status shouldBe 401
      response.body   shouldBe "Authorisation failure [Bearer token not supplied]"
    }

    "return 403 Forbidden when the user is logged in with an insufficient confidence level" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedInWithInsufficientConfidenceLevel()
      val response = await(requestWithAuthHeaders("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
      response.body   shouldBe "Authorisation failure [Insufficient ConfidenceLevel]"
    }
  }
}
