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

package uk.gov.hmrc.mobilehelptosave.controllers

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.config.TransactionControllerConfig
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.TestData
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, InternalAuthId, Shuttering}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.{ExecutionContext, Future}

class TransactionControllerSpec
  extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with FutureAwaits
    with TestData
    with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val generator = new Generator(0)
  private val nino = generator.nextNino
  private val otherNino = generator.nextNino
  private val internalAuthId = InternalAuthId("some-internal-auth-id")

  private val trueShuttering = Shuttering(shuttered = true, "Shuttered", "HTS is currently not available")
  private val falseShuttering = Shuttering(shuttered = false, "", "")

  private val MaxAgeHeaderValue = 1000
  private val config = TestTransactionControllerConfig(falseShuttering, MaxAgeHeaderValue)


  "getTransactions" should {

    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
      val controller = new TransactionController(logger, helpToSaveConnector, NeverAuthorisedWithIds, config)

      val resultF = controller.getTransactions(nino.value)(FakeRequest())
      status(resultF) shouldBe FORBIDDEN
    }
  }

  "getTransactions" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with transactions obtained by passing NINO to the HelpToSaveConnector" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
          .expects(nino, *, *)
          .returning(Future successful Right(Some(transactions)))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe OK
        val jsonBody = contentAsJson(resultF)
        jsonBody shouldBe Json.toJson(transactions)
      }
    }

    "include a maxAge header for a successful response" in {

      val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
      val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

      (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returning(Future successful Right(Some(transactions)))

      val resultF = controller.getTransactions(nino.value)(FakeRequest())
      getCacheControlHeaders(resultF) shouldBe Some(s"max-age=${config.maxAgeForSuccessInSeconds}")
    }

    "no account is found by HelpToSaveConnector for the NINO" should {
      "return 404" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
          .expects(nino, *, *)
          .returning(Future successful Right(None))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe NOT_FOUND
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "ACCOUNT_NOT_FOUND"
        (jsonBody \ "message").as[String] shouldBe "No Help to Save account exists for the specified NINO"

        getCacheControlHeaders(resultF) shouldBe None
      }
    }

    "HelpToSaveConnector returns an error" should {
      "return 500" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
          .expects(nino, *, *)
          .returning(Future successful Left(ErrorInfo("TEST_ERROR_CODE")))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe INTERNAL_SERVER_ERROR
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "TEST_ERROR_CODE"

        getCacheControlHeaders(resultF) shouldBe None
      }
    }

    "the NINO in the URL does not match the logged in user's NINO" should {
      "return 403" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        val resultF = controller.getTransactions(otherNino.value)(FakeRequest())
        status(resultF) shouldBe FORBIDDEN
        (slf4jLoggerStub.warn(_: String)) verify s"Attempt by ${nino.value} to access ${otherNino.value}'s transactions"

        getCacheControlHeaders(resultF) shouldBe None
      }
    }

    "the NINO is not in the correct format" should {
      "return 400 NINO_INVALID" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        val resultF = controller.getTransactions("invalidNino")(FakeRequest())
        status(resultF) shouldBe BAD_REQUEST
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
        (jsonBody \ "message").as[String] shouldBe """"invalidNino" does not match NINO validation regex"""

        getCacheControlHeaders(resultF) shouldBe None
      }
    }

    "the NINO in the URL contains spaces" should {
      "return 400 NINO_INVALID" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config)

        val resultF = controller.getTransactions("AA 00 00 03 D")(FakeRequest())
        status(resultF) shouldBe BAD_REQUEST
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
        (jsonBody \ "message").as[String] shouldBe """"AA 00 00 03 D" does not match NINO validation regex"""
      }
    }

    "helpToSaveShuttered = true" should {
      """return 503 "shuttered": true""" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino), config.copy(shuttering = trueShuttering))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe SERVICE_UNAVAILABLE
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "shuttered").as[Boolean] shouldBe true
        (jsonBody \ "title").as[String] shouldBe "Shuttered"
        (jsonBody \ "message").as[String] shouldBe "HTS is currently not available"

        getCacheControlHeaders(resultF) shouldBe None
      }
    }
  }

  private def getCacheControlHeaders(resultF: Future[Result]): Option[String] = {
    await(resultF).header.headers.get(HeaderNames.CACHE_CONTROL)
  }
}

private case class TestTransactionControllerConfig(shuttering: Shuttering, maxAgeForSuccessInSeconds:Long)
  extends TransactionControllerConfig
