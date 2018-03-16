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

package uk.gov.hmrc.mobilehelptosave.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino

object HelpToSaveProxyStub {

  def nsiAccountExists(nino: Nino, accountBalance: String): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTPMOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          Json.obj("accountBalance" -> accountBalance).toString
        )))

  def nsiAccountDoesNotExist(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTPMOBILE"))
      .willReturn(aResponse()
        .withStatus(400)
        .withBody(
          """
            |{
            |  "error": {
            |    "errorMessageId": "HTS-API015-006"
            |  }
            |}
          """.stripMargin)))

  def nsiAccountReturnsInternalServerError(): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .willReturn(aResponse()
        .withStatus(500)))

}
