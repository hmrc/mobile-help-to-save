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
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.TestData
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, InternalAuthId}
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

  "getTransactions" should {
    "ensure user is logged in and has a NINO by checking permissions using AuthorisedWithIds" in {
      val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
      val controller = new TransactionController(logger, helpToSaveConnector, NeverAuthorisedWithIds)

      val resultF = controller.getTransactions(nino.value)(FakeRequest())
      status(resultF) shouldBe 403
    }
  }

  "getTransactions" when {
    "logged in user's NINO matches NINO in URL" should {
      "return 200 with transactions obtained by passing NINO to the HelpToSaveConnector" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

        (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
          .expects(nino, *, *)
          .returning(Future successful Right(transactions))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe 200
        val jsonBody = contentAsJson(resultF)
        jsonBody shouldBe Json.toJson(transactions)
      }
    }

    "HelpToSaveConnector returns an error" should {
      "return 500" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

        (helpToSaveConnector.getTransactions(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
          .expects(nino, *, *)
          .returning(Future successful Left(ErrorInfo("TEST_ERROR_CODE")))

        val resultF = controller.getTransactions(nino.value)(FakeRequest())
        status(resultF) shouldBe 500
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "TEST_ERROR_CODE"
      }
    }

    "the NINO in the URL does not match the logged in user's NINO" should {
      "return 403" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

        val resultF = controller.getTransactions(otherNino.value)(FakeRequest())
        status(resultF) shouldBe 403
        (slf4jLoggerStub.warn(_: String)) verify s"Attempt by ${nino.value} to access ${otherNino.value}'s transactions"
      }
    }

    "the NINO is not in the correct format" should {
      "return 400 NINO_INVALID" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

        val resultF = controller.getTransactions("invalidNino")(FakeRequest())
        status(resultF) shouldBe 400
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
      }
    }

    "the NINO in the URL contains spaces" should {
      "return 400 NINO_INVALID" in {
        val helpToSaveConnector = mock[HelpToSaveConnectorGetTransactions]
        val controller = new TransactionController(logger, helpToSaveConnector, new AlwaysAuthorisedWithIds(internalAuthId, nino))

        val resultF = controller.getTransactions("AA 00 00 03 D")(FakeRequest())
        status(resultF) shouldBe 400
        val jsonBody = contentAsJson(resultF)
        (jsonBody \ "code").as[String] shouldBe "NINO_INVALID"
      }
    }

    "helpToSaveShuttered = true" should {
      // "code": SHUTTERED - or maybe SCHEDULED_MAINTENANCE per <confluence>/display/DTRG/How+to+emergency+shutter+an+API?focusedCommentId=88251625#comment-88251625
      //     "title": from setting, for example "Service Unavailable",
      //    "message": from setting, for example, for example "You’ll be able to use the Help to Save service at 9am on Monday 29 May 2017."
      "return 503 SHUTTERED" is pending
    }
  }
}
