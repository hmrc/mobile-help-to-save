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

import com.google.inject.ImplementedBy
import io.lemonlabs.uri.dsl._
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.JsValue
import uk.gov.hmrc.config.HelpToSaveConnectorConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.config.ScalaUriConfig.config
import uk.gov.hmrc.mobilehelptosave.config.SystemId.SystemId
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, Transactions}
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnectorEnrolmentStatus {
  def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]]
}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnectorGetTransactions {
  def getTransactions(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Transactions]]
}

@Singleton
class HelpToSaveConnectorImpl @Inject() (
  logger: LoggerLike,
  config: HelpToSaveConnectorConfig,
  http: CoreGet) extends HelpToSaveConnectorEnrolmentStatus with HelpToSaveConnectorGetTransactions {

  override def enrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Boolean]] = {
    http.GET[JsValue](enrolmentStatusUrl.toString) map { json: JsValue =>
      Right((json \ "enrolled").as[Boolean])
    } recover {
      case e@(_: HttpException | _: Upstream4xxResponse | _: Upstream5xxResponse) =>
        logger.warn("Couldn't get enrolment status from help-to-save service", e)
        Left(ErrorInfo.General)
    }
  }


  override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, Transactions]] = {
    val string = transactionsUrl(nino).toString
    http.GET[Transactions](string) map Right.apply recover {
      case e@(_: HttpException | _: Upstream4xxResponse | _: Upstream5xxResponse) =>
        logger.warn("Couldn't get transaction information from help-to-save service", e)
        Left(ErrorInfo.General)
    }
  }

  private lazy val enrolmentStatusUrl: URL = new URL(config.helpToSaveBaseUrl, "/help-to-save/enrolment-status")
  private def transactionsUrl(nino: Nino): URL = new URL(
    config.helpToSaveBaseUrl, s"/help-to-save/${encodePathSegment(nino.value)}/account/transactions" ? ("systemId" -> SystemId))
}
