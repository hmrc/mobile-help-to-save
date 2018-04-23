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

import com.typesafe.config.Config
import io.lemonlabs.uri._
import org.joda.time.LocalDate
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.mobilehelptosave.config.ScalaUriConfig.config
import uk.gov.hmrc.mobilehelptosave.support.{FakeHttpGet, LoggerStub, ThrowableWithMessageContaining}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HelpToSaveProxyConnectorSpec extends WordSpec with Matchers with MockFactory with OneInstancePerTest with LoggerStub with ThrowableWithMessageContaining with FutureAwaits with DefaultAwaitTimeout with OptionValues {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val nino = Nino("AA000000A")
  private val testBaseUrl = new URL("http://help-to-save-proxy-service")

  private def isAccountUrlForNino(urlString: String): Boolean = Url.parse(urlString) match {
    case AbsoluteUrl("http", Authority(_, Host("help-to-save-proxy-service"), None), AbsolutePath(Vector("help-to-save-proxy", "nsi-services", "account")), query, _)
      if query.param("nino").contains(nino.value) &&
         query.param("version").contains("V1.0") &&
         query.param("systemId").contains("MDTP-MOBILE") =>
      true
    case _ =>
      false
  }

  "nsiAccount" should {
    "return the account when native-app-widget returns 200 OK" in {
      val connector1 = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(
          200,
          Some(
            Json.parse(
              """
                |{
                |  "accountBalance": "200.34",
                |  "accountClosedFlag": "",
                |  "currentInvestmentMonth": {
                |    "investmentRemaining": "15.50",
                |    "investmentLimit": "50.00",
                |    "endDate": "2018-02-28"
                |  },
                |  "terms": [
                |     {
                |       "termNumber":2,
                |       "endDate":"2021-12-31",
                |       "bonusEstimate":"67.00",
                |       "bonusPaid":"0.00"
                |    },
                |    {
                |       "termNumber":1,
                |       "endDate":"2019-12-31",
                |       "bonusEstimate":"123.45",
                |       "bonusPaid":"123.45"
                |    }
                |  ]
                |}
              """.stripMargin)
          ))))

      await(connector1.nsiAccount(nino)) shouldBe Some(NsiAccount(
        accountClosedFlag = "",
        accountBalance = BigDecimal("200.34"),
        currentInvestmentMonth = NsiCurrentInvestmentMonth(
          investmentRemaining = BigDecimal("15.50"),
          investmentLimit = 50,
          endDate = new LocalDate(2018, 2, 28)),
        terms = Seq(
          NsiBonusTerm(termNumber = 2, endDate = new LocalDate(2021, 12, 31), bonusEstimate = 67, bonusPaid = 0),
          NsiBonusTerm(termNumber = 1, endDate = new LocalDate(2019, 12, 31), bonusEstimate = BigDecimal("123.45"), bonusPaid = BigDecimal("123.45"))
        )
      ))

      val connector2 = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(
          200,
          Some(
            Json.parse(
              """
                |{
                |  "accountBalance": "0.00",
                |  "accountClosedFlag": "C",
                |  "accountClosureDate": "2018-04-09",
                |  "accountClosingBalance": "10.11",
                |  "currentInvestmentMonth": {
                |    "endDate": "2018-04-30",
                |    "investmentRemaining": "12.34",
                |    "investmentLimit": "150.42"
                |  },
                |  "terms": []
                |}
              """.stripMargin)
          ))))

      await(connector2.nsiAccount(nino)) shouldBe Some(NsiAccount(
        accountClosedFlag = "C",
        accountBalance = 0,
        currentInvestmentMonth = NsiCurrentInvestmentMonth(
          investmentRemaining = BigDecimal("12.34"),
          investmentLimit = BigDecimal("150.42"),
          endDate = new LocalDate(2018, 4, 30)),
        terms = Seq.empty,
        accountClosureDate = Some(new LocalDate(2018, 4, 9)),
        accountClosingBalance = Some(BigDecimal("10.11"))))
    }

    "send a correlationId that is of an allowed length" in {
      var sentCorrelationId: Option[String] = None

      val http = new HttpGet {
        override val hooks: Seq[HttpHook] = Seq.empty

        override def configuration: Option[Config] = None

        override def doGet(urlString: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
          val url = Url.parse(urlString)
          Future.successful(url match {
            case AbsoluteUrl("http", Authority(_, Host("help-to-save-proxy-service"), None), AbsolutePath(Vector("help-to-save-proxy","nsi-services", "account")), query, _) =>
              sentCorrelationId = query.param("correlationId")
              HttpResponse(
                200,
                Some(
                  Json.parse(
                    """
                      |{
                      |  "accountClosedFlag": "",
                      |  "accountBalance": "200.34",
                      |  "currentInvestmentMonth": {
                      |    "investmentRemaining": "15.50",
                      |    "investmentLimit": "50.00",
                      |    "endDate": "2019-10-10"
                      |  },
                      |  "terms": []
                      |}
                    """.stripMargin)
                ))
            case _ =>
              HttpResponse(404)
          })
        }
      }

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, http)

      await(connector.nsiAccount(nino)) shouldBe Some(NsiAccount(
        accountClosedFlag = "",
        BigDecimal("200.34"),
        NsiCurrentInvestmentMonth(
          investmentRemaining = BigDecimal("15.50"),
          investmentLimit = 50,
          endDate = new LocalDate(2019, 10, 10)
        ),
        Seq.empty))

      sentCorrelationId.value.length should be <= 38
    }

    "return None when there is an error connecting to native-app-widget" in {
      val connectionRefusedHttp = FakeHttpGet(
        isAccountUrlForNino _,
        Future {
          throw new ConnectException("Connection refused")
        })

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, connectionRefusedHttp)

      await(connector.nsiAccount(nino)) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get account from help-to-save-proxy service""",
        throwableWithMessageContaining("Connection refused")
      )
    }

    "return None when native-app-widget returns a 4xx error" in {
      val error4xxHttp = FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(429))

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, error4xxHttp)

      await(connector.nsiAccount(nino)) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get account from help-to-save-proxy service""",
        throwableWithMessageContaining("429")
      )
    }

    "return None when native-app-widget returns a 5xx error" in {
      val error5xxHttp = FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(500))

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, error5xxHttp)

      await(connector.nsiAccount(nino)) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get account from help-to-save-proxy service""",
        throwableWithMessageContaining("500")
      )
    }

    "return None when native-app-widget returns JSON that is missing fields that are required according to get_account_by_nino_RESP_schema_V1.0.json" in {
      val invalidJsonHttp = FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(
          200,
          Some(
            Json.parse(
              // invalid because required field bonusPaid is omitted from first term
              """
                |{
                |  "accountBalance": "123.45",
                |  "currentInvestmentMonth": {
                |    "investmentRemaining": "15.50",
                |    "investmentLimit": "50.00"
                |  },
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
                |}""".stripMargin)
          )))

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, invalidJsonHttp)

      await(connector.nsiAccount(nino)) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get account from help-to-save-proxy service""",
        throwableWithMessageContaining("invalid json")
      )
    }
  }
}
