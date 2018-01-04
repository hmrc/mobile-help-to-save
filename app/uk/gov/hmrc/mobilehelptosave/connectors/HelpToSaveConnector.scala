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
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.Mode.Mode
import play.api.libs.json.JsValue
import play.api.{Configuration, Environment, LoggerLike}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (
  logger: LoggerLike,
  config: HelpToSaveConnectorConfig,
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

  private val enrolmentStatusUrl = new URL(config.serviceUrl, "/help-to-save/enrolment-status")

}

@ImplementedBy(classOf[HelpToSaveConnectorConfigImpl])
trait HelpToSaveConnectorConfig {
  val serviceUrl: URL
}

@Singleton
class HelpToSaveConnectorConfigImpl @Inject() (
  override val runModeConfiguration: Configuration,
  environment: Environment
) extends HelpToSaveConnectorConfig with ServicesConfig {

  override val serviceUrl = new URL(baseUrl("help-to-save"))

  override protected def mode: Mode = environment.mode
}
