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
         query.param("systemId").contains("MDTPMOBILE") =>
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
                |  "accountBalance": "200.34"
                |}
              """.stripMargin)
          ))))

      await(connector1.nsiAccount(nino)) shouldBe Some(NsiAccount(BigDecimal("200.34")))

      val connector2 = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, FakeHttpGet(
        isAccountUrlForNino _,
        HttpResponse(
          200,
          Some(
            Json.parse(
              """
                |{
                |  "accountBalance": "200.00"
                |}
              """.stripMargin)
          ))))

      await(connector2.nsiAccount(nino)) shouldBe Some(NsiAccount(BigDecimal("200.00")))
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
                      |  "accountBalance": "200.34"
                      |}
                    """.stripMargin)
                ))
            case _ =>
              HttpResponse(404)
          })
        }
      }

      val connector = new HelpToSaveProxyConnectorImpl(logger, testBaseUrl, http)

      await(connector.nsiAccount(nino)) shouldBe Some(NsiAccount(BigDecimal("200.34")))

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
  }
}
