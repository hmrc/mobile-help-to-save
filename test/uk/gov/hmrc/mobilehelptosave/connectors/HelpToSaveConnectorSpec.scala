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

import io.lemonlabs.uri._
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import play.api.libs.json.Json
import uk.gov.hmrc.config.HelpToSaveConnectorConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.TestData
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo
import uk.gov.hmrc.mobilehelptosave.support.{FakeHttpGet, LoggerStub, ThrowableWithMessageContaining}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

class HelpToSaveConnectorSpec extends UnitSpec with MockFactory with OneInstancePerTest with LoggerStub with ThrowableWithMessageContaining with TestData {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val baseUrl = "http://help-to-save-service"

  private val ninoString = "AA000000A"
  private val nino = Nino(ninoString)

  private def isTransactionsUrlForNino(urlString: String): Boolean = Url.parse(urlString) match {
    case AbsoluteUrl("http", Authority(_, Host("help-to-save-service"), None), AbsolutePath(Vector("help-to-save", this.ninoString, "account", "transactions")), query, _)
      if query.param("systemId").contains("MDTP-MOBILE") =>
      true
    case _ =>
      info(s"URL failed to match: $urlString")
      false
  }

  private def httpGet(path: String, response: Future[HttpResponse]) = FakeHttpGet(s"$baseUrl/help-to-save/$path", response)

  private def httpGet(predicate: String => Boolean, response: Future[HttpResponse]) = FakeHttpGet(predicate, response)


  private val config: HelpToSaveConnectorConfig = new HelpToSaveConnectorConfig {
    override val helpToSaveBaseUrl: URL = new URL(baseUrl)
  }

  "getTransactions" should {

    val errorMessage = """Couldn't get transaction information from help-to-save service"""

    "return a Left when there is an error connecting to the help-to-save service" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet(isTransactionsUrlForNino _, failed(new ConnectException("Connection refused"))))

      await(connector.getTransactions(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(errorMessage, throwableWithMessageContaining("Connection refused"))
    }

    "return a Left when the help-to-save service returns a 4xx error" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet(isTransactionsUrlForNino _, HttpResponse(429)))

      await(connector.getTransactions(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(errorMessage, throwableWithMessageContaining("429"))
    }

    "return a Left when the help-to-save service returns a 5xx error" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet(isTransactionsUrlForNino _, HttpResponse(500)))

      await(connector.getTransactions(nino)) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(errorMessage, throwableWithMessageContaining("500"))
    }

    "return a Right (with Transactions) when the help-to-save service returns a 2xx response" in {

      val okResponse = httpGet(isTransactionsUrlForNino _, HttpResponse(200, Some(Json.parse(transactionsReturnedByHelpToSaveJsonString))))

      val connector = new HelpToSaveConnectorImpl(logger, config, okResponse)

      await(connector.getTransactions(nino)) shouldBe Right(Some(transactions))
    }

    "return Right(None) when the help-to-save service returns a 404 response" in {

      val notFoundResponse = httpGet(isTransactionsUrlForNino _, HttpResponse(404))

      val connector = new HelpToSaveConnectorImpl(logger, config, notFoundResponse)

      await(connector.getTransactions(nino)) shouldBe Right(None)
    }
  }

  "enrolmentStatus" should {

    "return a Left when there is an error connecting to the help-to-save service" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet("enrolment-status", failed(new ConnectException("Connection refused"))))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify("""Couldn't get enrolment status from help-to-save service""", throwableWithMessageContaining("Connection refused"))
    }

    "return a Left when the help-to-save service returns a 4xx error" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet("enrolment-status", HttpResponse(429)))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify("""Couldn't get enrolment status from help-to-save service""", throwableWithMessageContaining("429"))
    }

    "return a Left when the help-to-save service returns a 5xx error" in {

      val connector = new HelpToSaveConnectorImpl(logger, config, httpGet("enrolment-status", HttpResponse(500)))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""",
        throwableWithMessageContaining("500")
      )
    }

    "return a Right when the help-to-save service returns a 2xx response" in {

      val okResponse = httpGet("enrolment-status", HttpResponse(200, Some(Json.parse("""{ "enrolled": true }"""))))
      val connector = new HelpToSaveConnectorImpl(logger, config, okResponse)

      await(connector.enrolmentStatus()) shouldBe Right(true)
    }
  }
}
