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
import java.util.UUID

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.ImplementedBy
import io.lemonlabs.uri.dsl._
import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.api.LoggerLike
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.config.HelpToSaveProxyConnectorConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.config.ScalaUriConfig.config
import uk.gov.hmrc.mobilehelptosave.config.SystemId.SystemId
import uk.gov.hmrc.mobilehelptosave.domain.ErrorInfo

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveProxyConnectorImpl])
trait HelpToSaveProxyConnector {

  def nsiAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, NsiAccount]]

}

@Singleton
class HelpToSaveProxyConnectorImpl @Inject() (
  logger: LoggerLike,
  config: HelpToSaveProxyConnectorConfig,
  http: CoreGet
) extends HelpToSaveProxyConnector {

  override def nsiAccount(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorInfo, NsiAccount]] = {
    http.GET[NsiAccount](nsiAccountUrl(nino)) map Right.apply
  } recover {
    case e@(_: HttpException | _: Upstream4xxResponse | _: Upstream5xxResponse | _: JsValidationException | _: JsonParseException) =>
      logger.warn("Couldn't get account from help-to-save-proxy service", e)
      Left(ErrorInfo.General)
  }

  private def nsiAccountUrl(nino: Nino) = {
    val correlationId = UUID.randomUUID().toString
    new URL(config.helpToSaveProxyBaseUrl, "/help-to-save-proxy/nsi-services/account").toString ? ("nino" -> nino.value) & ("version" -> "V1.0") & ("systemId" -> SystemId) & ("correlationId" -> correlationId)
  }

}

case class NsiCurrentInvestmentMonth(
  investmentRemaining: BigDecimal,
  investmentLimit: BigDecimal,
  endDate: LocalDate
)

object NsiCurrentInvestmentMonth {
  implicit val reads: Reads[NsiCurrentInvestmentMonth] = Json.reads[NsiCurrentInvestmentMonth]
}

case class NsiBonusTerm(
  termNumber: Int,
  startDate: LocalDate,
  endDate: LocalDate,
  bonusEstimate: BigDecimal,
  bonusPaid: BigDecimal
)

object NsiBonusTerm {
  implicit val reads: Reads[NsiBonusTerm] = Json.reads[NsiBonusTerm]
}

case class NsiAccount(
  accountClosedFlag: String,
  accountBlockingCode: String,
  clientBlockingCode: String,
  accountBalance: BigDecimal,
  currentInvestmentMonth: NsiCurrentInvestmentMonth,
  terms: Seq[NsiBonusTerm],
  accountClosureDate: Option[LocalDate] = None,
  accountClosingBalance: Option[BigDecimal] = None
)

object NsiAccount {
  implicit val reads: Reads[NsiAccount] = Json.reads[NsiAccount]
}
