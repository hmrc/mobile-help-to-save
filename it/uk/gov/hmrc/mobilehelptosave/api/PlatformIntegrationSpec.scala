/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.api

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, WordSpec}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test.{FakeRequest, PlayRunners}
import uk.gov.hmrc.mobilehelptosave.stubs.ServiceLocatorStub
import uk.gov.hmrc.mobilehelptosave.support.WireMockSupport

/**
  * Testcase to verify the capability of integration with the API platform.
  *
  * 1, To integrate with API platform the service needs to register itself to the service locator by calling the /registration endpoint and providing
  * - application name
  * - application url
  *
  * 2a, To expose API's to Third Party Developers, the service needs to define the APIs in a definition.json and make it available under api/definition GET endpoint
  * 2b, For all of the endpoints defined in the definition.json a documentation.xml needs to be provided and be available under api/documentation/[version]/[endpoint name] GET endpoint
  * Example: api/documentation/1.0/Fetch-Some-Data
  *
  * See: confluence ApiPlatform/API+Platform+Architecture+with+Flows
  */
class PlatformIntegrationSpec extends WordSpec with Matchers with Eventually with WireMockSupport with PlayRunners {

  trait Setup {
    val documentationController: DocumentationController = app.injector.instanceOf[DocumentationController]
    val request = FakeRequest()

    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
  }

  lazy val app: Application = appBuilder.build()

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder.configure(
    "microservice.services.service-locator.host" -> wireMockHost,
    "microservice.services.service-locator.port" -> wireMockPort,
    "api.access.white-list.applicationIds" -> Seq("00010002-0003-0004-0005-000600070008", "00090002-0003-0004-0005-000600070008"),
    "api.access.type" -> "TEST_ACCESS_TYPE"
  )

  "microservice" should {
    "register itself with the api platform automatically at start up" in {
      ServiceLocatorStub.registerShouldNotHaveBeenCalled()

      running(app) {
        eventually(Timeout(Span(1000 * 20, Millis))) {
          ServiceLocatorStub.registerShouldHaveBeenCalled()
        }
      }
    }

    "provide definition with configurable whitelist" in new Setup {
      running(app) {

        val result = documentationController.definition()(request)
        status(result) shouldBe 200

        val definition = contentAsJson(result)
        (definition \\ "version").map(_.as[String]).head shouldBe "1.0"

        val accessConfigs = definition \ "api" \ "versions" \\ "access"
        accessConfigs.length should be > 0
        accessConfigs.foreach { accessConfig =>
          (accessConfig \ "type").as[String] shouldBe "TEST_ACCESS_TYPE"
          (accessConfig \ "whitelistedApplicationIds").head.as[String] shouldBe "00010002-0003-0004-0005-000600070008"
          (accessConfig \ "whitelistedApplicationIds") (1).as[String] shouldBe "00090002-0003-0004-0005-000600070008"
        }
      }
    }

    "provide RAML conf endpoint" in new Setup {
      running(app) {
        val result = documentationController.conf("1.0", "application.raml")(request)
        status(result) shouldBe 200
      }
    }
  }
}

