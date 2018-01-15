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

import play.api.Application
import uk.gov.hmrc.mobilehelptosave.stubs.{HelpToSaveStub, NativeAppWidgetStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

class StartupISpec extends UnitSpec with WireMockSupport with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = wireMockApplicationBuilder().build()

  "GET /mobile-help-to-save/startup" should {

    "include user.state" in {
      HelpToSaveStub.currentUserIsEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
    }

    "return user.state = NotEnrolled when user is not already enrolled and has not indicated that they wanted to be contacted" in {
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
    }

    "return user.state = InvitedFirstTime when user is not already enrolled and has indicated that they wanted to be contacted" in {
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserWantsToBeContacted()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")
    }

    "omit user state if call to help-to-save fails" in {
      HelpToSaveStub.enrolmentStatusReturnsInternalServerError()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      // check that only the user state field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
    }

    "omit user state if call to native-app-widget to get survey answers fails" in {
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.gettingAnswersReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      // check that only the user state field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
    }
  }
}
