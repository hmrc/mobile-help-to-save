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

import java.util.UUID

import org.scalatest.BeforeAndAfterEach
import play.api.Application
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.mobilehelptosave.repos.InvitationRepository
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, NativeAppWidgetStub}
import uk.gov.hmrc.mobilehelptosave.support.{OneServerPerSuiteWsClient, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class StartupISpec extends UnitSpec with WireMockSupport with OneServerPerSuiteWsClient with BeforeAndAfterEach {

  override implicit lazy val app: Application = wireMockApplicationBuilder()
    .configure(InvitationConfig.Enabled)
    .build()

  private var internalAuthId: InternalAuthId = _

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" should {

    "include user.state" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("Enrolled")
    }

    "return user.state = NotEnrolled when user is not already enrolled and has not indicated that they wanted to be contacted" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("NotEnrolled")
    }

    "return user.state = InvitedFirstTime and then user.state = Invited when user is not already enrolled and has indicated that they wanted to be contacted" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserWantsToBeContacted()

      val response1 = await(wsUrl("/mobile-help-to-save/startup").get())
      response1.status shouldBe 200
      (response1.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")

      val response2 = await(wsUrl("/mobile-help-to-save/startup").get())
      response2.status shouldBe 200
      (response2.json \ "user" \ "state").asOpt[String] shouldBe Some("Invited")
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

    "omit user state if call to native-app-widget to get survey answers fails" in {
      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.gettingAnswersReturnsInternalServerError()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe None
      // check that only the user state field has been omitted, not all fields
      (response.json \ "enabled").asOpt[Boolean] should not be None
      (response.json \ "infoUrl").asOpt[String] should not be None
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

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    internalAuthId = new InternalAuthId(s"test-${UUID.randomUUID()}}")
  }

  override protected def afterEach(): Unit = {
    await(app.injector.instanceOf[InvitationRepository].removeById(internalAuthId))
    super.afterEach()
  }
}
