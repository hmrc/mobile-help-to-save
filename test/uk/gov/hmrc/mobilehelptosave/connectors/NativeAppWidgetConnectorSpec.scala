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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.support.{FakeHttpGet, LoggerStub, ThrowableWithMessageContaining}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NativeAppWidgetConnectorSpec extends WordSpec with Matchers with MockFactory with OneInstancePerTest with LoggerStub with ThrowableWithMessageContaining with FutureAwaits with DefaultAwaitTimeout {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "answers" should {
    "return the answers when native-app-widget returns 200 OK" in {
      val nativeAppWidgetAnswersResponse = HttpResponse(
        200,
        Some(
          Json.parse(
            """
              |[
              |  {
              |    "content": "Yes",
              |    "contentType": "String",
              |    "additionalInfo": "Would you like us to contact you?"
              |  },
              |  {
              |    "content": "No",
              |    "contentType": "String",
              |    "additionalInfo": "Would you like us to contact you?"
              |  }
              |]
            """.stripMargin)
        )
      )

      val fakeHttp = FakeHttpGet(
        "http://native-app-widget-service/native-app-widget/widget-data/TEST_CAMPAIGN_ID/test_question_1",
        nativeAppWidgetAnswersResponse)

      val connector = new NativeAppWidgetConnectorImpl(logger, new URL("http://native-app-widget-service"), fakeHttp)

      await(connector.answers("TEST_CAMPAIGN_ID", "test_question_1")) shouldBe Some(Seq("Yes", "No"))
    }

    "return None when there is an error connecting to native-app-widget" in {
      val connectionRefusedHttp = FakeHttpGet(
        "http://native-app-widget-service/native-app-widget/widget-data/TEST_CAMPAIGN_ID/test_question_1",
        Future {
          throw new ConnectException("Connection refused")
        })

      val connector = new NativeAppWidgetConnectorImpl(logger, new URL("http://native-app-widget-service"), connectionRefusedHttp)

      await(connector.answers("TEST_CAMPAIGN_ID", "test_question_1")) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get answers from native-app-widget service""",
        throwableWithMessageContaining("Connection refused")
      )
    }

    "return None when native-app-widget returns a 4xx error" in {
      val error4xxHttp = FakeHttpGet(
        "http://native-app-widget-service/native-app-widget/widget-data/TEST_CAMPAIGN_ID/test_question_1",
        HttpResponse(429))

      val connector = new NativeAppWidgetConnectorImpl(logger, new URL("http://native-app-widget-service"), error4xxHttp)

      await(connector.answers("TEST_CAMPAIGN_ID", "test_question_1")) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get answers from native-app-widget service""",
        throwableWithMessageContaining("429")
      )
    }

    "return None when native-app-widget returns a 5xx error" in {
      val error5xxHttp = FakeHttpGet(
        "http://native-app-widget-service/native-app-widget/widget-data/TEST_CAMPAIGN_ID/test_question_1",
        HttpResponse(500))

      val connector = new NativeAppWidgetConnectorImpl(logger, new URL("http://native-app-widget-service"), error5xxHttp)

      await(connector.answers("TEST_CAMPAIGN_ID", "test_question_1")) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get answers from native-app-widget service""",
        throwableWithMessageContaining("500")
      )
    }
  }
}
