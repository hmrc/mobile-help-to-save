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

import java.net.ConnectException

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.config.HelpToSaveConfig
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

  private val baseUrl = "http://help-to-save-service:80"

  private def httpGet(path: String, response:Future[HttpResponse]) = FakeHttpGet(s"$baseUrl/help-to-save/$path", response)
  private val connectionRefusedHttp = (path:String) => httpGet(path, failed(new ConnectException("Connection refused")))
  private val error4xxHttp = (path:String) => httpGet(path, HttpResponse(429))
  private val error5xxHttp = (path:String) => httpGet(path, HttpResponse(500))
  private val okHttp       = (path:String, json:Option[JsValue]) => httpGet(path, HttpResponse(200, json))

  private val config: HelpToSaveConfig = HelpToSaveConfig.from(
    Map (
      "microservice.services.help-to-save.protocol" -> "http",
      "microservice.services.help-to-save.port" -> "80",
      "microservice.services.help-to-save.host" -> "help-to-save-service"
    )
  )

  private def connectorWithResponseFor(httpGet:FakeHttpGet) = {
    new HelpToSaveConnectorImpl(logger, config, httpGet)
  }

  "getTransactions" should {

    val errorMessage = """Couldn't get transaction information from help-to-save service"""
    val transactionsPath = "JC338899B/account/transactions"

    "return a Left when there is an error connecting to the help-to-save service" in {

      val connector = connectorWithResponseFor(connectionRefusedHttp(transactionsPath))

      await(connector.getTransactions("JC338899B")) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify (errorMessage, throwableWithMessageContaining("Connection refused"))
    }

    "return a Left when the help-to-save service returns a 4xx error" in {

      val connector = connectorWithResponseFor(error4xxHttp(transactionsPath))

      await(connector.getTransactions("JC338899B")) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify (errorMessage,throwableWithMessageContaining("429"))
    }

    "return a Left when the help-to-save service returns a 5xx error" in {

      val connector = connectorWithResponseFor(error5xxHttp(transactionsPath))

      await(connector.getTransactions("JC338899B")) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(errorMessage,throwableWithMessageContaining("500"))
    }

    "return a Right (with Transactions) when the help-to-save service returns a 2xx response" in {

      val okResponse = okHttp(transactionsPath, Some(Json.parse(transactionsJsonString)))

      val connector = connectorWithResponseFor(okResponse)

      await(connector.getTransactions("JC338899B")) shouldBe Right(transactionsJson)
    }
  }

  "enrolmentStatus" should {

    val errorMessage = """Couldn't get enrolment status from help-to-save service"""
    val enrolmentStatusPath = "enrolment-status"

    "return a Left when there is an error connecting to the help-to-save service" in {

      val connector = connectorWithResponseFor(connectionRefusedHttp(enrolmentStatusPath))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify (errorMessage, throwableWithMessageContaining("Connection refused"))
    }

    "return a Left when the help-to-save service returns a 4xx error" in {

      val connector = connectorWithResponseFor(error4xxHttp(enrolmentStatusPath))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify (errorMessage,throwableWithMessageContaining("429"))
    }

    "return a Left when the help-to-save service returns a 5xx error" in {

      val connector = connectorWithResponseFor(error5xxHttp(enrolmentStatusPath))

      await(connector.enrolmentStatus()) shouldBe Left(ErrorInfo.General)

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        errorMessage,
        throwableWithMessageContaining("500")
      )
    }

    "return a Right when the help-to-save service returns a 2xx response" in {

      val okResponse = okHttp(enrolmentStatusPath, Some(Json.parse("""{ "enrolled": true }""")))
      val connector = connectorWithResponseFor(okResponse)

      await(connector.enrolmentStatus()) shouldBe Right(true)
    }
  }
}
