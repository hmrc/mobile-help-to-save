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

import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.libs.ws.WSClient
import uk.gov.hmrc.mobilehelptosave.stubs.HelpToSaveStub
import uk.gov.hmrc.mobilehelptosave.support.{WireMockSupport, WithTestServer}
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Tests that the startup endpoint uses configuration values correctly
  * (e.g. changes its response when configuration is changed).
  */
class StartupConfigISpec extends UnitSpec with WsScalaTestClient with WireMockSupport with WithTestServer {

  "GET /mobile-help-to-save/startup" should {
    "return enabled=true when configuration value helpToSave.enabled=true" in withTestServer(
      wireMockApplicationBuilder()
        .configure("helpToSave.enabled" -> true)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
    }

    "return enabled=false when configuration value helpToSave.enabled=false" in withTestServer(
      wireMockApplicationBuilder()
        .configure("helpToSave.enabled" -> false)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe false
    }

    "include infoUrl and invitationUrl obtained from configuration" in withTestServer(
      wireMockApplicationBuilder()
        .configure(
          "helpToSave.infoUrl" -> "http://www.example.com/test/help-to-save-information",
          "helpToSave.invitationUrl" -> "http://www.example.com/test/help-to-save-invitation"
        )
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      HelpToSaveStub.currentUserIsNotEnrolled()

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "infoUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-information"
      (response.json \ "invitationUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-invitation"
    }
  }
}
