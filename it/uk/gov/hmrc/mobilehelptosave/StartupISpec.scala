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

import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.libs.json.JsUndefined
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveProxyStub, HelpToSaveStub, NativeAppWidgetStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}

class StartupISpec extends WordSpec with Matchers
  with FutureAwaits with DefaultAwaitTimeout with InvitationCleanup
  with WireMockSupport with OneServerPerSuiteWsClient {

  override implicit lazy val app: Application = wireMockApplicationBuilder()
    .configure(InvitationConfig.Enabled)
    .build()

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" should {

    "include user.state and user.account" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()
      HelpToSaveProxyStub.nsiAccountExists(nino, "123.45")

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      // check it is "balance": 123.45 not "balance": "123.45"
      (response.json \ "user" \ "account" \ "balance").asOpt[String] shouldBe None
      (response.json \ "user" \ "account" \ "balance").as[BigDecimal] shouldBe BigDecimal("123.45")
    }

    "integrate with the metrics returned by /admin/metrics" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserWantsToBeContacted()

      def invitationCountMetric(): Integer = {
        val metricsResponse = await(wsUrl("/admin/metrics").get())
        (metricsResponse.json \ "counters" \ "invitation" \ "count").as[Int]
      }

      val invitationCountBefore = invitationCountMetric()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

      val invitationCountAfter = invitationCountMetric()

      (invitationCountAfter - invitationCountBefore) shouldBe 1
    }

    "omit user state if call to help-to-save fails" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.enrolmentStatusReturnsInternalServerError()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      // check that only the user state field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
    }

    "omit account details but still include user state if call to get account fails" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()
      HelpToSaveProxyStub.nsiAccountReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
      (response.json \ "user" \ "account") shouldBe a [JsUndefined]
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 401
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 403
    }
  }
}
