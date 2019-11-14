/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URL

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.{BadGatewayException, CoreGet, HeaderCarrier, HttpReads, InternalServerException}
import uk.gov.hmrc.mobilehelptosave.config.ShutteringConnectorConfig
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ExecutionContext, Future}

class ShutteringConnectorSpec extends WordSpec with Matchers with FutureAwaits with DefaultAwaitTimeout with MockFactory with OneInstancePerTest {
  val mockCoreGet: CoreGet = mock[CoreGet]
  private val config: ShutteringConnectorConfig = new ShutteringConnectorConfig {
    override val shutteringBaseUrl: URL = new URL("http:///")
  }
  val connector:           ShutteringConnector = new ShutteringConnector(mockCoreGet, config)
  private implicit val hc: HeaderCarrier       = HeaderCarrier()

  def mockShutteringGet[T](f: Future[T]) =
    (mockCoreGet
      .GET(_: String)(_: HttpReads[T], _: HeaderCarrier, _: ExecutionContext))
      .expects("http://mobile-shuttering/service/mobile-help-to-save/shuttered-status?journeyId=journeyId", *, *, *)
      .returning(f)

  "getTaxReconciliations" should {
    "Assume unshuttered for InternalServerException response" in {
      mockShutteringGet(Future.successful(new InternalServerException("")))

      val result: Shuttering = await(connector.getShutteringStatus("journeyId"))
      result shouldBe Shuttering.shutteringDisabled
    }

    "Assume unshuttered for BadGatewayException response" in {
      mockShutteringGet(Future.successful(new BadGatewayException("")))

      val result: Shuttering = await(connector.getShutteringStatus("journeyId"))
      result shouldBe Shuttering.shutteringDisabled
    }
  }
}
