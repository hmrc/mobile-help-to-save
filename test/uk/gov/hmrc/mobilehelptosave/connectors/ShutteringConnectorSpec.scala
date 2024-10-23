/*
 * Copyright 2024 HM Revenue & Customs
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


import org.mockito.Mockito.when

import java.net.URL
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mobilehelptosave.config.ShutteringConnectorConfig
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ShutteringConnectorSpec extends HttpClientV2Helper {

  private val config: ShutteringConnectorConfig = new ShutteringConnectorConfig {
    override val shutteringBaseUrl: URL = new URL("http:///")
  }

  val connector = new ShutteringConnector(mockHttpClient, config)
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val mockHttp = mock[HttpClientV2]

  "getTaxReconciliations" should {
    "Assume unshuttered for InternalServerException response" in {
      when(requestBuilderExecute[HttpResponse])
        .thenReturn(Future.failed(UpstreamErrorResponse("INTERNAL SERVER ERROR", 500)))

      connector.getShutteringStatus("journeyId") onComplete {
        case Success(_) => Shuttering.shutteringDisabled
        case Failure(_) =>
      }
    }
    "Assume unshuttered for BadGatewayException response" in {
      when(requestBuilderExecute[HttpResponse])
        .thenReturn(Future.failed(UpstreamErrorResponse("BAD GATEWAY", 502)))

      connector.getShutteringStatus("journeyId") onComplete {
        case Success(_) => Shuttering.shutteringDisabled
        case Failure(_) =>
      }
    }
  }
}
