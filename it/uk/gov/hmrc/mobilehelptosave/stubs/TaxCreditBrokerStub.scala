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
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegments

object TaxCreditBrokerStub {

  def userHasFeb2018WorkingTaxCreditsPayments(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(encodePathSegments("tcs", nino.value, "payment-summary")))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |  "workingTaxCredit": {
            |    "previousPaymentSeq": [
            |      {
            |        "amount": 255.48,
            |        "paymentDate": 1517356800000
            |      },
            |      {
            |        "amount": 255.48,
            |        "paymentDate": 1517961600000
            |      }
            |    ]
            |  }
            |}
          """.stripMargin
        )))

  def userHasNoWorkingTaxCreditsPayments(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(encodePathSegments("tcs", nino.value, "payment-summary")))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
            |{
            |  "workingTaxCredit": {}
            |}
          """.stripMargin
        )))

  /**
    * This is called paymentSummaryReturnsExcluded, not
    * userIsExcludedFromTaxCredits, because payment-summary returns
    * {"excluded":true} when the NINO is unknown, not just when the NINO is
    * explicitly excluded (at least in stubbed environments - haven't
    * verified what happens when running against the real HoD).
    */
  def paymentSummaryReturnsExcluded(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(encodePathSegments("tcs", nino.value, "payment-summary")))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """
             |{
             |  "excluded": true
             |}
          """.stripMargin
        )))

  def gettingTaxCreditsPaymentsReturnsInternalServerError(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(encodePathSegments("tcs", nino.value, "payment-summary")))
      .willReturn(aResponse()
        .withStatus(500)))

  def paymentSummaryShouldOnlyHaveBeenCalledOnce(nino: Nino): Unit =
    verify(1, getRequestedFor(urlPathEqualTo(encodePathSegments("tcs", nino.value, "payment-summary"))))

}
