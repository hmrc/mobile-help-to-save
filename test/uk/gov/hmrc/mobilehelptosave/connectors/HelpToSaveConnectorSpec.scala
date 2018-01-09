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
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.slf4j.Logger
import play.api.LoggerLike
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveConnectorSpec extends UnitSpec with MockFactory with OneInstancePerTest {

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)
  private val slf4jLoggerStub = stub[Logger]
  (slf4jLoggerStub.isWarnEnabled: () => Boolean).when().returning(true)
  private val logger = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "enrolmentStatus" should {
    "return None when there is an error connecting to the help-to-save service" in {
      val connectExceptionToThrow = new ConnectException("Connection refused")
      val connectionRefusedHttp = new HttpGet {
        override def configuration: Option[Config] = None

        override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
          Future failed connectExceptionToThrow
        }

        override val hooks: Seq[HttpHook] = Seq.empty
      }

      val connector = new HelpToSaveConnectorImpl(logger, FakeHelpToSaveConnectorConfig, connectionRefusedHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""",
        argThat((e: Throwable) => e.getMessage.contains("Connection refused"))
      )
    }

    "return None when the help-to-save service returns a 4xx error" in {
      val thrownException = Upstream4xxResponse("message", 429, 500)
      val error4xxHttp = new CoreGet {
        override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
          Future { throw thrownException }
        override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
          Future { throw thrownException }
      }

      val connector = new HelpToSaveConnectorImpl(logger, FakeHelpToSaveConnectorConfig, error4xxHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""", thrownException)
    }

    "return None when the help-to-save service returns a 5xx error" in {
      val thrownException = Upstream5xxResponse("message", 500, 502)
      val error5xxHttp = new CoreGet {
        override def GET[A](url: String)(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
          Future { throw thrownException }
        override def GET[A](url: String, queryParams: Seq[(String, String)])(implicit rds: HttpReads[A], hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
          Future { throw thrownException }
      }

      val connector = new HelpToSaveConnectorImpl(logger, FakeHelpToSaveConnectorConfig, error5xxHttp)

      await(connector.enrolmentStatus()) shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify(
        """Couldn't get enrolment status from help-to-save service""", thrownException)
    }
  }

  private object FakeHelpToSaveConnectorConfig extends HelpToSaveConnectorConfig {
    override val serviceUrl: URL = new URL("http://help-to-save-service/")
  }

}
