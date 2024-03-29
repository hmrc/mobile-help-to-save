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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID.randomUUID
import org.scalatestplus.play.components.WithApplicationComponents
import org.scalatestplus.play.{PortNumber, WsScalaTestClient}
import play.api.Application
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.stubs.{AuthStub, HelpToSaveStub, ShutteringStub}
import uk.gov.hmrc.mobilehelptosave.support.{ComponentSupport, JsonMatchers, WireMockSupport, WithTestServer}

/**
  * Tests that the Get Account endpoint uses configuration values correctly
  * (e.g. changes its response when configuration is changed).
  */
class AccountConfigISpec
    extends AnyWordSpecLike
    with Matchers
    with JsonMatchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with WsScalaTestClient
    with WireMockSupport
    with WithTestServer
    with ComponentSupport
    with WithApplicationComponents {

  private val generator = new Generator(0)
  private val nino      = generator.nextNino
  private val journeyId = "27085215-69a4-4027-8f72-b04b10ec16b0"

  private val acceptJsonHeader:        (String, String) = "Accept"        -> "application/vnd.hmrc.1.0+json"
  private val authorisationJsonHeader: (String, String) = "AUTHORIZATION" -> "Bearer 123"

  "GET /savings-account/{nino} and /sandbox/savings-account/{nino}" should {
    "allow inAppPaymentsEnabled to be overridden with configuration" in {
      ShutteringStub.stubForShutteringDisabled()
      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(1, nino)

      withTestServer(
        appBuilder
          .configure(
            "helpToSave.inAppPaymentsEnabled" -> "false"
          )
          .build()
      ) { (app: Application, portNumber: PortNumber) =>
        implicit val implicitPortNumber: PortNumber = portNumber
        implicit val wsClient:           WSClient   = components.wsClient

        responseShouldHaveInAppPaymentsEqualTo(await(
                                                 wsUrl(s"/savings-account/$nino?journeyId=$journeyId")
                                                   .addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
                                                   .get()
                                               ),
                                               expectedValue = false)
        responseShouldHaveInAppPaymentsEqualTo(
          await(wsUrl(s"/sandbox/savings-account/$nino?journeyId=$journeyId").get()),
          expectedValue = false
        )
      }

      withTestServer(
        appBuilder
          .configure(
            "helpToSave.inAppPaymentsEnabled" -> "true"
          )
          .build()
      ) { (app: Application, portNumber: PortNumber) =>
        implicit val implicitPortNumber: PortNumber = portNumber
        implicit val wsClient:           WSClient   = components.wsClient

        responseShouldHaveInAppPaymentsEqualTo(await(
                                                 wsUrl(s"/savings-account/$nino?journeyId=$journeyId")
                                                   .addHttpHeaders(acceptJsonHeader, authorisationJsonHeader)
                                                   .get()
                                               ),
                                               expectedValue = true)
        responseShouldHaveInAppPaymentsEqualTo(
          await(wsUrl(s"/sandbox/savings-account/$nino?journeyId=$journeyId").get()),
          expectedValue = true
        )
      }
    }
    "return 400 if no journeyId is supplied" in {

      AuthStub.userIsLoggedIn(nino)
      HelpToSaveStub.currentUserIsEnrolled()
      HelpToSaveStub.accountExists(1, nino = nino)

      withTestServer(
        appBuilder
          .configure(
            "helpToSave.inAppPaymentsEnabled" -> "true"
          )
          .build()
      ) { (app: Application, portNumber: PortNumber) =>
        implicit val implicitPortNumber: PortNumber = portNumber
        implicit val wsClient:           WSClient   = components.wsClient

        val response: WSResponse = await(wsUrl(s"/savings-account/$nino").get())
        response.status shouldBe (400)
        val sandboxResponse: WSResponse = await(
          wsUrl(s"/sandbox/savings-account/$nino").addHttpHeaders(acceptJsonHeader, authorisationJsonHeader).get()
        )
        sandboxResponse.status shouldBe (400)
      }
    }
  }

  private def responseShouldHaveInAppPaymentsEqualTo(
    response:      WSResponse,
    expectedValue: Boolean
  ) = {
    response.status shouldBe 200

    (response.json \ "inAppPaymentsEnabled").as[Boolean] shouldBe expectedValue
  }

}
