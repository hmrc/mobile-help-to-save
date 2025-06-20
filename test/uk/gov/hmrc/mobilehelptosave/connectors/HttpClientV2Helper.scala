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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.mobilehelptosave.support.BaseSpec

import java.net.URL
import scala.concurrent.ExecutionContext

trait HttpClientV2Helper extends BaseSpec with ScalaFutures {

  val requestBuilder: RequestBuilder = mock[RequestBuilder]
  when(mockHttpClient.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(mockHttpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(mockHttpClient.delete(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(mockHttpClient.put(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(requestBuilder.transform(any())).thenReturn(requestBuilder)
  when(requestBuilder.withBody(any[JsValue])(using any(), any(), any())).thenReturn(requestBuilder)

  def requestBuilderExecute[A] = requestBuilder.execute[A](using any[HttpReads[A]], any[ExecutionContext])

}
