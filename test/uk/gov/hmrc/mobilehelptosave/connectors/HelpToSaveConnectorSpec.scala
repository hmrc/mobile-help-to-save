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
import org.scalatest.OneInstancePerTest
import org.slf4j.Logger
import play.api.LoggerLike
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.support.{FakeHttpGet, ThrowableWithMessageContaining}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HelpToSaveConnectorSpec extends UnitSpec with MockFactory with OneInstancePerTest with ThrowableWithMessageContaining {

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)
  private val slf4jLoggerStub = stub[Logger]
  (slf4jLoggerStub.isWarnEnabled: () => Boolean).when().returning(true)
  private val logger = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "enrolmentStatus" should {
    "return None when there is an error connecting to the help-to-save service" in {
      val connectionRefusedHttp = FakeHttpGet(
        "http://help-to-save-service/help-to-save/enrolment-status",
        Future {
          throw new ConnectException("Connection refused")
        })

      val connector = new HelpToSaveConnectorImpl(logger, new URL("http://help-to-save-service/"), connectionRefusedHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""",
        throwableWithMessageContaining("Connection refused")
      )
    }

    "return None when the help-to-save service returns a 4xx error" in {
      val error4xxHttp = FakeHttpGet(
        "http://help-to-save-service/help-to-save/enrolment-status",
        HttpResponse(429))

      val connector = new HelpToSaveConnectorImpl(logger, new URL("http://help-to-save-service/"), error4xxHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""",
        throwableWithMessageContaining("429")
      )
    }

    "return None when the help-to-save service returns a 5xx error" in {
      val error5xxHttp = FakeHttpGet(
        "http://help-to-save-service/help-to-save/enrolment-status",
        HttpResponse(500))

      val connector = new HelpToSaveConnectorImpl(logger, new URL("http://help-to-save-service/"), error5xxHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""",
        throwableWithMessageContaining("500")
      )
    }
  }

}
