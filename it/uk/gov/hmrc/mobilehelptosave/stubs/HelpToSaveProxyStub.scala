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
import uk.gov.hmrc.domain.Nino

object HelpToSaveProxyStub {

  def nsiAccountExists(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |  "accountBalance": "123.45",
            |  "accountClosedFlag": "",
            |  "accountBlockingCode": "00",
            |  "clientBlockingCode": "00",
            |  "currentInvestmentMonth": {
            |    "endDate": "2018-02-28",
            |    "investmentRemaining": "22.12",
            |    "investmentLimit": "50.00"
            |  },
            |  "terms": [
            |     {
            |       "termNumber":1,
            |       "startDate":"2018-01-01",
            |       "endDate":"2019-12-31",
            |       "bonusEstimate":"90.99",
            |       "bonusPaid":"90.99"
            |    },
            |    {
            |       "termNumber":2,
            |       "startDate":"2020-01-01",
            |       "endDate":"2021-12-31",
            |       "bonusEstimate":"12.00",
            |       "bonusPaid":"00.00"
            |    }
            |  ]
            |}
          """.stripMargin
        )))

  def closedNsiAccountExists(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |  "accountBalance": "0.00",
            |  "accountClosedFlag": "C",
            |  "accountBlockingCode": "00",
            |  "clientBlockingCode": "00",
            |  "accountClosureDate": "2018-04-09",
            |  "accountClosingBalance": "10.00",
            |  "currentInvestmentMonth": {
            |    "endDate": "2018-04-30",
            |    "investmentLimit": "50.00",
            |    "investmentRemaining": "50.00"
            |  },
            |  "terms": [
            |     {
            |       "termNumber":1,
            |       "startDate": "2018-03-01",
            |       "endDate": "2020-02-29",
            |       "bonusEstimate": "7.50",
            |       "bonusPaid": "0.00"
            |    },
            |    {
            |       "termNumber":2,
            |       "startDate": "2020-03-01",
            |       "endDate": "2022-02-28",
            |       "bonusEstimate": "0.00",
            |       "bonusPaid": "0.00"
            |    }
            |  ]
            |}
          """.stripMargin
        )))

  def nsiAccountReturnsInvalidAccordingToSchemaJson(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          // invalid because required field bonusPaid is omitted from first term
          """
            |{
            |  "accountBalance": "123.45",
            |  "terms": [
            |     {
            |       "termNumber":1,
            |       "endDate":"2019-12-31",
            |       "bonusEstimate":"90.99"
            |    },
            |    {
            |       "termNumber":2,
            |       "endDate":"2021-12-31",
            |       "bonusEstimate":"12.00",
            |       "bonusPaid":"00.00"
            |    }
            |  ]
            |}
          """.stripMargin
        )))

  def nsiAccountReturnsBadlyFormedJson(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """not JSON""".stripMargin
        )))

  def nsiAccountDoesNotExist(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo("/help-to-save-proxy/nsi-services/account"))
      .withQueryParam("nino", equalTo(nino.value))
      .withQueryParam("version", equalTo("V1.0"))
      .withQueryParam("systemId", equalTo("MDTP-MOBILE"))
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

  def nsiAccountShouldNotHaveBeenCalled(): Unit =
    verify(0, getRequestedFor(urlPathEqualTo("/help-to-save-proxy/nsi-services/account")))

}
