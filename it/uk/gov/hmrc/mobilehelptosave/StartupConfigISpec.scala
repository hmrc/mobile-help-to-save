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
import org.scalatestplus.play.components.WithApplicationComponents
import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.libs.json.JsObject
import play.api.libs.ws.WSClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, JsonMatchers, WireMockSupport, WithTestServer}

/**
  * Tests that the startup endpoint uses configuration values correctly
  * (e.g. changes its response when configuration is changed).
  */
class StartupConfigISpec
    extends WordSpec
    with Matchers
    with JsonMatchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with WsScalaTestClient
    with WireMockSupport
    with WithTestServer
    with ComponentSupport
    with WithApplicationComponents {

  private val generator     = new Generator(0)
  private val nino          = generator.nextNino
  private val base64Encoder = Base64.getEncoder

  "GET /mobile-help-to-save/startup" should {
    "not call other microservices and only include shuttering information and feature flags when helpToSave.shuttering.shuttered = true" in withTestServer(
      appBuilder
        .configure(
          "helpToSave.shuttering.shuttered" -> true,
          "helpToSave.shuttering.title"     -> base64Encode("Shuttered"),
          "helpToSave.shuttering.message"   -> base64Encode("HTS is currently not available")
        )
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient:           WSClient   = components.wsClient

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())

      response.status                                          shouldBe 200
      (response.json \ "shuttering" \ "shuttered").as[Boolean] shouldBe true
      (response.json \ "shuttering" \ "title").as[String]      shouldBe "Shuttered"
      (response.json \ "shuttering" \ "message").as[String]    shouldBe "HTS is currently not available"
      response.json.as[JsObject].keys                          should not contain "user"

      AuthStub.authoriseShouldNotHaveBeenCalled()
      HelpToSaveStub.enrolmentStatusShouldNotHaveBeenCalled()
    }

    "include feature flag and URL settings when their configuration is not overridden" in withTestServer(
      appBuilder
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient:           WSClient   = components.wsClient

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status                                          shouldBe 200
      (response.json \ "infoUrl").as[String]                   shouldBe "http://localhost:8249/mobile-help-to-save/info"
      (response.json \ "accessAccountUrl").as[String]          shouldBe "http://localhost:8249/mobile-help-to-save/access-account"
      (response.json \ "accountPayInUrl").as[String]           shouldBe "http://localhost:8249/mobile-help-to-save/pay-in"
    }

    "allow feature flag and URL settings to be overridden in configuration" in {
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsNotEnrolled()

      withTestServer(
        appBuilder
          .configure(
            "helpToSave.infoUrl"            -> "http://www.example.com/test/help-to-save-information",
            "helpToSave.accessAccountUrl"   -> "/access-account",
            "helpToSave.accountPayInUrl"    -> "/pay-in"
          )
          .build()) { (app: Application, portNumber: PortNumber) =>
        implicit val implicitPortNumber: PortNumber = portNumber
        implicit val wsClient:           WSClient   = components.wsClient

        val response = await(wsUrl("/mobile-help-to-save/startup").get())
        response.status                                    shouldBe 200
        (response.json \ "infoUrl").as[String]             shouldBe "http://www.example.com/test/help-to-save-information"
        (response.json \ "accessAccountUrl").as[String]    shouldBe "/access-account"
        (response.json \ "accountPayInUrl").as[String]     shouldBe "/pay-in"
      }
    }
  }

  private def base64Encode(s: String): String =
    base64Encoder.encodeToString(s.getBytes("UTF-8"))
}
