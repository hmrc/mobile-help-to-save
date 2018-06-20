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

import java.util.Base64

import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveProxyStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{JsonMatchers, MongoTestCollections, WireMockSupport, WithTestServer}

/**
  * Tests that the startup endpoint uses configuration values correctly
  * (e.g. changes its response when configuration is changed).
  */
class StartupConfigISpec extends WordSpec with Matchers with JsonMatchers with FutureAwaits with DefaultAwaitTimeout
  with WsScalaTestClient with WireMockSupport with MongoTestCollections with WithTestServer {

  private val internalAuthId = InternalAuthId("test-internal-auth-id")
  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val base64Encoder = Base64.getEncoder

  "GET /mobile-help-to-save/startup" should {
    "return enabled=true when configuration value helpToSave.enabled=true" in withTestServerAndMongoCleanup(
      appBuilder
        .configure("helpToSave.enabled" -> true)
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
    }

    "return enabled=false, omit all other fields from the response and not call help-to-save when configuration value helpToSave.enabled=false" in withTestServerAndMongoCleanup(
      appBuilder
        .configure("helpToSave.enabled" -> false)
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe false
      response.json.as[JsObject] - "enabled" shouldBe Json.obj()

      HelpToSaveStub.enrolmentStatusShouldNotHaveBeenCalled()
    }

    "not call other microservices and only include shuttering information and feature flags when helpToSave.shuttering.shuttered = true" in withTestServerAndMongoCleanup(
      appBuilder
        .configure(
          "helpToSave.shuttering.shuttered" -> true,
          "helpToSave.shuttering.title" -> base64Encode("Shuttered"),
          "helpToSave.shuttering.message" -> base64Encode("HTS is currently not available"),
          "helpToSave.enabled" -> true,
          "helpToSave.savingRemindersEnabled" -> true
        )
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())

      response.status shouldBe 200
      (response.json \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
      (response.json \ "shuttering" \ "title").as[String] shouldBe "Shuttered"
      (response.json \ "shuttering" \ "message").as[String] shouldBe "HTS is currently not available"
      (response.json \ "enabled").as[Boolean] shouldBe true
      (response.json \ "savingRemindersEnabled").as[Boolean] shouldBe true
      response.json.as[JsObject].keys should not contain "user"

      AuthStub.authoriseShouldNotHaveBeenCalled()
      HelpToSaveStub.enrolmentStatusShouldNotHaveBeenCalled()
      HelpToSaveProxyStub.nsiAccountShouldNotHaveBeenCalled()
    }

    "not call Get Account API when feature flags that require account information are all disabled" in withTestServerAndMongoCleanup(
      appBuilder
        .configure(
          "helpToSave.shuttering.shuttered" -> false,
          "helpToSave.enabled" -> true,
          "helpToSave.shareInvitationEnabled" -> true,
          "helpToSave.savingRemindersEnabled" -> true,
          "helpToSave.balanceEnabled" -> false,
          "helpToSave.paidInThisMonthEnabled" -> false,
          "helpToSave.firstBonusEnabled" -> false
        )
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveProxyStub.nsiAccountExists(nino)

      val response = await(wsUrl("/mobile-help-to-save/startup").get())

      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
      (response.json \ "user" \ "state").as[String] shouldBe "Enrolled"
      (response.json \ "user").as[JsObject].keys should not contain "account"

      HelpToSaveProxyStub.nsiAccountShouldNotHaveBeenCalled()
    }

    "include feature flag and URL settings when their configuration is not overridden" in withTestServerAndMongoCleanup(
      appBuilder
        .configure("helpToSave.enabled" -> true)
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "balanceEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "paidInThisMonthEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "firstBonusEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "shareInvitationEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "savingRemindersEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "transactionsEnabled").validate[Boolean] should beJsSuccess
      (response.json \ "infoUrl").as[String] shouldBe "https://www.gov.uk/government/publications/help-to-save-what-it-is-and-who-its-for/the-help-to-save-scheme"
      (response.json \ "invitationUrl").as[String] shouldBe "http://localhost:8249/mobile-help-to-save"
      (response.json \ "accessAccountUrl").as[String] shouldBe "http://localhost:8249/mobile-help-to-save/access-account"
    }


    "allow feature flag and URL settings to be overridden in configuration" in withTestServerAndMongoCleanup(
      appBuilder
        .configure(
          "helpToSave.infoUrl" -> "http://www.example.com/test/help-to-save-information",
          "helpToSave.invitationUrl" -> "http://www.example.com/test/help-to-save-invitation",
          "helpToSave.accessAccountUrl" -> "/access-account",
          "helpToSave.balanceEnabled" -> "true",
          "helpToSave.paidInThisMonthEnabled" -> "true",
          "helpToSave.firstBonusEnabled" -> "true",
          "helpToSave.shareInvitationEnabled" -> "true",
          "helpToSave.savingRemindersEnabled" -> "true",
          "helpToSave.transactionsEnabled" -> "true"
        )
        .configure(InvitationConfig.NoFilters: _*)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      AuthStub.userIsLoggedIn(internalAuthId, nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "balanceEnabled").as[Boolean] shouldBe true
      (response.json \ "paidInThisMonthEnabled").as[Boolean] shouldBe true
      (response.json \ "firstBonusEnabled").as[Boolean] shouldBe true
      (response.json \ "shareInvitationEnabled").as[Boolean] shouldBe true
      (response.json \ "savingRemindersEnabled").as[Boolean] shouldBe true
      (response.json \ "transactionsEnabled").as[Boolean] shouldBe true
      (response.json \ "infoUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-information"
      (response.json \ "invitationUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-invitation"
      (response.json \ "accessAccountUrl").as[String] shouldBe "/access-account"
    }

  }

  private def base64Encode(s: String): String = {
    base64Encoder.encodeToString(s.getBytes("UTF-8"))
  }

  private def withTestServerAndMongoCleanup[R](app: Application)(testCode: (Application, PortNumber) => R): R =
    withTestServer(app) { (app: Application, portNumber: PortNumber) =>
      try {
        testCode(app, portNumber)
      } finally {
        await(dropTestCollections(db(app)))
      }
    }
}
