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

package uk.gov.hmrc.mobilehelptosave.connectors

import java.net.{ConnectException, URL}

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.{JsResultException, Json}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.config.TaxCreditsBrokerConnectorConfig
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo
import uk.gov.hmrc.mobilehelptosave.support.{FakeHttpGet, LoggerStub, ThrowableWithMessageContaining}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCreditsBrokerConnectorSpec extends WordSpec with Matchers with MockFactory with OneInstancePerTest with LoggerStub with ThrowableWithMessageContaining with FutureAwaits with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val paymentSummaryForNinoUrl = s"http://tax-credits-broker-service/tcs/${nino.value}/payment-summary"
  private val config: TaxCreditsBrokerConnectorConfig = new TaxCreditsBrokerConnectorConfig {
    override val taxCreditsBrokerBaseUrl: URL = new URL("http://tax-credits-broker-service")
  }


  "previousPayments" should {
    "return the payments when tax-credits-broker returns 200 OK" in {
      val taxCreditsBrokerResponse = HttpResponse(
        200,
        Some(
          Json.parse(
            """
              |{
              |  "workingTaxCredit": {
              |    "previousPaymentSeq": [
              |      {
              |        "amount": 160.45,
              |        "oneOffPayment": false,
              |        "paymentDate": 1521676800000
              |      },
              |      {
              |        "amount": 160.45,
              |        "oneOffPayment": false,
              |        "paymentDate": 1522278000000
              |      },
              |      {
              |        "amount": 123.45,
              |        "oneOffPayment": true,
              |        "paymentDate": 1522882800000
              |      }
              |    ]
              |  }
              |}
            """.stripMargin)
        )
      )

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Right(Seq(
        Payment(BigDecimal("160.45"), new DateTime("2018-03-22T00:00:00.000Z")),
        Payment(BigDecimal("160.45"), new DateTime("2018-03-28T23:00:00.000Z")),
        Payment(BigDecimal("123.45"), new DateTime("2018-04-04T23:00:00.000Z"))
      ))
    }

    "return empty Seq and not log a warning when tax-credits-broker returns 200 OK with excluded: true" in {
      // We don't log a warning because /tcs/:nino/payment-summary when
      // called with an unknown NINO seems to return a 200 with "excluded": true
      // rather than a 404, so if we logged a warning we'd log a warning
      // every time we made a call for a NINO that had never had tax credits.
      // We return Some(Seq.empty) instead of None for a similar reason - this
      // is not an exceptional condition so we don't want to indicate failure
      // by returning None.
      val taxCreditsBrokerResponse = HttpResponse(
        200,
        Some(Json.parse("""{"excluded":true}"""))
      )

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Right(Seq.empty)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(*, *) never()
      (slf4jLoggerStub.warn(_: String)) verify * never()
    }

    "return an error when tax-credits-broker returns 404 Not Found" in {
      // As far as I can tell /tcs/:nino/payment-summary will never return a 404 at present.
      // However I think it should do so (instead of returning excluded) when called with an unknown NINO.
      // This test ensures that this microservice will continue to work if tax-credits-broker is fixed to do that.
      val taxCreditsBrokerResponse = HttpResponse(404)

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Left(ErrorInfo.General)
    }

    "return an empty Seq when tax-credits-broker does not include workingTaxCredit in its response" in {
      val taxCreditsBrokerResponse = HttpResponse(
        200,
        Some(Json.parse("""{}"""))
      )

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Right(Seq.empty[Payment])
    }

    "return an empty Seq when tax-credits-broker does not include workingTaxCredit.previousPaymentSeq in its response" in {
      val taxCreditsBrokerResponse = HttpResponse(
        200,
        Some(
          Json.parse(
            """
              |{
              |  "workingTaxCredit": {
              |    "paymentSeq": [
              |      {
              |        "amount": 160.45,
              |        "oneOffPayment": false,
              |        "paymentDate": 1521676800000
              |      }
              |    ]
              |  }
              |}
            """.stripMargin)
        )
      )

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Right(Seq.empty[Payment])
    }

    "return an error and log a warning when the payments cannot be parsed" in {
      val taxCreditsBrokerResponse = HttpResponse(
        200,
        Some(
          Json.parse(
            """
              |{
              |  "workingTaxCredit": {
              |    "previousPaymentSeq": [
              |      {
              |        "amount": "not parsable",
              |        "oneOffPayment": false,
              |        "paymentDate": 1521676800000
              |      }
              |    ]
              |  }
              |}
            """.stripMargin)
        )
      )

      val fakeHttp = FakeHttpGet(paymentSummaryForNinoUrl, taxCreditsBrokerResponse)

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, fakeHttp)

      await(connector.previousPayments(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get payments from tax-credits-broker service""",
        argThat((e: Throwable) => e.isInstanceOf[JsResultException])
      )
    }

    "return an error and log a warning when there is an error connecting to tax-credits-broker" in {
      val connectionRefusedHttp = FakeHttpGet(
        paymentSummaryForNinoUrl,
        Future {
          throw new ConnectException("Connection refused")
        })

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, connectionRefusedHttp)

      await(connector.previousPayments(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get payments from tax-credits-broker service""",
        throwableWithMessageContaining("Connection refused")
      )
    }

    "return an error and log a warning when tax-credits-broker returns a 4xx error" in {
      val error4xxHttp = FakeHttpGet(
        paymentSummaryForNinoUrl,
        HttpResponse(429))

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, error4xxHttp)

      await(connector.previousPayments(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get payments from tax-credits-broker service""",
        throwableWithMessageContaining("429")
      )
    }

    "return an error and log a warning when tax-credits-broker returns a 5xx error" in {
      val error5xxHttp = FakeHttpGet(
        paymentSummaryForNinoUrl,
        HttpResponse(500))

      val connector = new TaxCreditsBrokerConnectorImpl(logger, config, error5xxHttp)

      await(connector.previousPayments(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get payments from tax-credits-broker service""",
        throwableWithMessageContaining("500")
      )
    }
  }

}
