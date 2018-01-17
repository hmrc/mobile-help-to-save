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

package uk.gov.hmrc.mobilehelptosave.support

import com.typesafe.config.Config
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpResponse}

import scala.concurrent.Future

class FakeHttpGet(expectedUrl: String, responseF: Future[HttpResponse]) extends HttpGet {

  override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    if (url == expectedUrl)
      responseF
  else
      Future successful HttpResponse(404)

  override def configuration: Option[Config] = None

  override val hooks: Seq[HttpHook] = Seq.empty

}

object FakeHttpGet {

  def apply(expectedUrl: String, responseF: Future[HttpResponse]) = new FakeHttpGet(expectedUrl, responseF)
  def apply(expectedUrl: String, response: HttpResponse) = new FakeHttpGet(expectedUrl, Future successful response)

}
