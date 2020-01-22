/*
 * Copyright 2017 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.client.WireMock._

object ShutteringStub {

  def stubForShutteringDisabled()(implicit wireMockServer: WireMockServer): StubMapping =
    wireMockServer.stubFor(
      get(urlPathEqualTo("/mobile-shuttering/service/mobile-help-to-save/shuttered-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "shuttered": false,
                         |  "title":     "",
                         |  "message":    ""
                         |}
          """.stripMargin)
        )
    )

  def stubForShutteringEnabled()(implicit wireMockServer: WireMockServer): StubMapping =
    wireMockServer.stubFor(
      get(urlPathEqualTo("/mobile-shuttering/service/mobile-help-to-save/shuttered-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "shuttered": true,
                         |  "title":     "Shuttered",
                         |  "message":   "HTS is currently not available"
                         |}
          """.stripMargin)
        )
    )

}
