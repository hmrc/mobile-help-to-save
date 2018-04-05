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

import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.mobilehelptosave.repos.InvitationRepository
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, NativeAppWidgetStub}
import uk.gov.hmrc.mobilehelptosave.support.{WireMockSupport, WithTestServer}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Tests that the startup endpoint uses configuration values correctly
  * (e.g. changes its response when configuration is changed).
  */
class StartupConfigISpec extends UnitSpec with WsScalaTestClient with WireMockSupport with WithTestServer {

  private val internalAuthId = InternalAuthId("test-internal-auth-id")
  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "GET /mobile-help-to-save/startup" should {
    "return enabled=true when configuration value helpToSave.enabled=true" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure("helpToSave.enabled" -> true)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
    }

    "return enabled=false, omit all other fields from the response and not call help-to-save when configuration value helpToSave.enabled=false" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure("helpToSave.enabled" -> false)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe false
      response.json.as[JsObject] - "enabled" shouldBe Json.obj()

      HelpToSaveStub.enrolmentStatusShouldNotHaveBeenCalled()
    }

    "only include shuttering information and feature flags when helpToSave.shuttering.shuttered = true" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure(
          "helpToSave.shuttering.shuttered" -> true,
          "helpToSave.enabled" -> true,
          "helpToSave.savingRemindersEnabled" -> true
        )
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]
      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
      (response.json \ "enabled").as[Boolean] shouldBe true
      (response.json \ "savingRemindersEnabled").as[Boolean] shouldBe true
      response.json.as[JsObject].keys should not contain "user"

      HelpToSaveStub.enrolmentStatusShouldNotHaveBeenCalled()
    }

    "include default feature flag and URL settings when their configuration is not overridden" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure("helpToSave.enabled" -> true)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "balanceEnabled").as[Boolean] shouldBe false
      (response.json \ "paidInThisMonthEnabled").as[Boolean] shouldBe false
      (response.json \ "firstBonusEnabled").as[Boolean] shouldBe false
      (response.json \ "shareInvitationEnabled").as[Boolean] shouldBe false
      (response.json \ "savingRemindersEnabled").as[Boolean] shouldBe false
      (response.json \ "infoUrl").as[String] shouldBe "https://www.gov.uk/government/publications/help-to-save-what-it-is-and-who-its-for/the-help-to-save-scheme"
      (response.json \ "invitationUrl").as[String] shouldBe "http://localhost:8249/mobile-help-to-save"
      (response.json \ "accessAccountUrl").as[String] shouldBe "http://localhost:8249/mobile-help-to-save/access-account"
    }


    "allow feature flag and URL settings to be overridden in configuration" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure(
          "helpToSave.infoUrl" -> "http://www.example.com/test/help-to-save-information",
          "helpToSave.invitationUrl" -> "http://www.example.com/test/help-to-save-invitation",
          "helpToSave.accessAccountUrl" -> "/access-account",
          "helpToSave.balanceEnabled" -> "true",
          "helpToSave.paidInThisMonthEnabled" -> "true",
          "helpToSave.firstBonusEnabled" -> "true",
          "helpToSave.shareInvitationEnabled" -> "true",
          "helpToSave.savingRemindersEnabled" -> "true"
        )
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "balanceEnabled").as[Boolean] shouldBe true
      (response.json \ "paidInThisMonthEnabled").as[Boolean] shouldBe true
      (response.json \ "firstBonusEnabled").as[Boolean] shouldBe true
      (response.json \ "shareInvitationEnabled").as[Boolean] shouldBe true
      (response.json \ "savingRemindersEnabled").as[Boolean] shouldBe true
      (response.json \ "infoUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-information"
      (response.json \ "invitationUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-invitation"
      (response.json \ "accessAccountUrl").as[String] shouldBe "/access-account"
    }

    "invite user regardless of survey response when helpToSave.invitationFilters.survey=false" in withTestServerAndInvitationCleanup(
      wireMockApplicationBuilder()
        .configure(
          InvitationConfig.Enabled,
          "helpToSave.invitationFilters.survey" -> "false"
        ).build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()
      NativeAppWidgetStub.currentUserHasNotRespondedToSurvey()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "user" \ "state").asOpt[String] shouldBe Some("InvitedFirstTime")
    }

  }

  private def withTestServerAndInvitationCleanup[R](app: Application)(testCode: (Application, PortNumber) => R): R =
    withTestServer(app) { (app: Application, portNumber: PortNumber) =>
      try {
        testCode(app, portNumber)
      } finally {
        await(app.injector.instanceOf[InvitationRepository].removeById(internalAuthId))
      }
    }
}
