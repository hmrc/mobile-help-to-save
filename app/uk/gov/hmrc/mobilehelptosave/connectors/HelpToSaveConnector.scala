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

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import play.api.LoggerLike
import play.api.libs.json.JsValue
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (
  logger: LoggerLike,
  @Named("help-to-save-baseUrl") baseUrl: URL,
  http: CoreGet) extends HelpToSaveConnector {

  override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {
    http.GET[JsValue](enrolmentStatusUrl.toString) map { json: JsValue =>
      Some((json \ "enrolled").as[Boolean])
    } recover {
      case e@(_: HttpException | _: Upstream4xxResponse | _: Upstream5xxResponse) =>
        logger.warn("Couldn't get enrolment status from help-to-save service", e)
        None
    }
  }

  private val enrolmentStatusUrl = new URL(baseUrl, "/help-to-save/enrolment-status")

}
