/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.stubs

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino

object AuthStub {

  private val authoriseRequestBody: String = {
    """
      |{
      | "authorise": [{"confidenceLevel": 200}],
      | "retrieve": ["nino"]
      |}""".stripMargin
  }

  def userIsLoggedIn(nino: Nino)(implicit wireMockServer: WireMockServer): StubMapping =
    wireMockServer.stubFor(
      post(urlPathEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(authoriseRequestBody))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(Json.obj("nino" -> nino.value).toString)
        )
    )

  def userIsLoggedInWithInsufficientConfidenceLevel()(implicit wireMockServer: WireMockServer): StubMapping =
    wireMockServer.stubFor(
      post(urlPathEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(authoriseRequestBody))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", """MDTP detail="InsufficientConfidenceLevel"""")
        )
    )

  def userIsNotLoggedIn()(implicit wireMockServer: WireMockServer): StubMapping =
    wireMockServer.stubFor(
      post(urlPathEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(authoriseRequestBody))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", """MDTP detail="MissingBearerToken"""")
        )
    )

  def authoriseShouldNotHaveBeenCalled()(implicit wireMockServer: WireMockServer): Unit =
    wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/auth/authorise")))
}
