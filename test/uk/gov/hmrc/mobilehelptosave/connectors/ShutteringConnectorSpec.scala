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

///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.mobilehelptosave.connectors
//
//import org.scalatestplus.mockito.MockitoSugar
//
//import java.net.URL
//import uk.gov.hmrc.http._
//import uk.gov.hmrc.http.client.HttpClientV2
//import uk.gov.hmrc.mobilehelptosave.config.ShutteringConnectorConfig
//import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
//import uk.gov.hmrc.mobilehelptosave.support.{BaseSpec, FakeHttpGet}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class ShutteringConnectorSpec extends BaseSpec with MockitoSugar {
//
//  private val config: ShutteringConnectorConfig = new ShutteringConnectorConfig {
//    override val shutteringBaseUrl: URL = new URL("http:///")
//  }
//  private implicit val hc: HeaderCarrier = HeaderCarrier()
//  lazy val mockHttp = mock[HttpClientV2]
//
//  private def httpGet(response: HttpResponse) =
//    FakeHttpGet(s"http://mobile-shuttering/service/mobile-help-to-save/shuttered-status?journeyId=journeyId", response)
//
//  val internalServerExceptionResponse: FakeHttpGet =
//    httpGet(
//      HttpResponse(
//        500,
//        HttpErrorFunctions.upstreamResponseMessage(
//          "GET",
//          "http://mobile-shuttering/service/mobile-help-to-save/shuttered-status?journeyId=journeyId",
//          500,
//          "INTERNAL SERVER ERROR"
//        )
//      )
//    )
//
//  val badGatewayResponse: FakeHttpGet =
//    httpGet(
//      HttpResponse(
//        502,
//        HttpErrorFunctions.upstreamResponseMessage(
//          "GET",
//          "http://mobile-shuttering/service/mobile-help-to-save/shuttered-status?journeyId=journeyId",
//          502,
//          "BAD GATEWAY"
//        )
//      )
//    )
//
//  def connector(response: FakeHttpGet) = new ShutteringConnector(response, config)
//
//  "getTaxReconciliations" should {
//    "Assume unshuttered for InternalServerException response" in {
//      val result = await(connector(internalServerExceptionResponse).getShutteringStatus("journeyId"))
//      result shouldBe Shuttering.shutteringDisabled
//    }
//
//    "Assume unshuttered for BadGatewayException response" in {
//      val result: Shuttering = await(connector(badGatewayResponse).getShutteringStatus("journeyId"))
//      result shouldBe Shuttering.shutteringDisabled
//    }
//  }
//}
