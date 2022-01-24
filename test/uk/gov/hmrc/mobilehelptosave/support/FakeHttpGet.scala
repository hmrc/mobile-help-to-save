/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpResponse}
import scala.concurrent.{ExecutionContext, Future}

class FakeHttpGet(
  urlPredicate: String => Boolean,
  responseF:    Future[HttpResponse])
    extends HttpGet {

  override def doGet(
    url:         String,
    headers:     Seq[(String, String)] = Seq.empty
  )(implicit ec:          ExecutionContext
  ): Future[HttpResponse] =
    if (urlPredicate(url))
      responseF
    else
      Future successful HttpResponse(404)

  override def configuration:         Config =  Configuration.load(Environment.simple()).underlying
  override val hooks:                 Seq[HttpHook]  = Seq.empty
  override protected def actorSystem: ActorSystem    = ActorSystem()
}

object FakeHttpGet {

  def apply(
    expectedUrl: String,
    responseF:   Future[HttpResponse]
  ) = new FakeHttpGet(_ == expectedUrl, responseF)

  def apply(
    expectedUrl: String,
    response:    HttpResponse
  ) = new FakeHttpGet(_ == expectedUrl, Future successful response)

  def apply(
    urlPredicate: String => Boolean,
    responseF:    Future[HttpResponse]
  ) = new FakeHttpGet(urlPredicate, responseF)

  def apply(
    urlPredicate: String => Boolean,
    response:     HttpResponse
  ) = new FakeHttpGet(urlPredicate, Future successful response)
}
