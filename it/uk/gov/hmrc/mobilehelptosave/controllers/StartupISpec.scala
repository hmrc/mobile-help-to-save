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

import org.scalatestplus.play.guice.GuiceOneServerPerTest
import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.{Helpers, TestServer}
import uk.gov.hmrc.play.test.UnitSpec

class StartupISpec extends UnitSpec with GuiceOneServerPerTest with WsScalaTestClient {
  "GET /mobile-help-to-save/startup" should {
    "return enabled=true when configuration value helpToSave.enabled=true" in withTestServer(
      new GuiceApplicationBuilder()
        .configure("helpToSave.enabled" -> true)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe true
    }

    "return enabled=false when configuration value helpToSave.enabled=false" in withTestServer(
      new GuiceApplicationBuilder()
        .configure("helpToSave.enabled" -> false)
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "enabled").as[Boolean] shouldBe false
    }

    "include infoUrl obtained from configuration" in withTestServer(
      new GuiceApplicationBuilder()
        .configure("helpToSave.infoUrl" -> "http://www.example.com/test/help-to-save-information")
        .build()) { (app: Application, portNumber: PortNumber) =>
      implicit val implicitPortNumber: PortNumber = portNumber
      implicit val wsClient: WSClient = app.injector.instanceOf[WSClient]

      val response = await(wsUrl("/mobile-help-to-save/startup").get())
      response.status shouldBe 200
      (response.json \ "infoUrl").as[String] shouldBe "http://www.example.com/test/help-to-save-information"
    }
  }

  private def withTestServer(app: Application)(testCode: (Application, PortNumber) => Any) = {
    val port: Int = Helpers.testServerPort
    Helpers.running(TestServer(port, app)) {
      testCode(app, PortNumber(port))
    }
  }
}
