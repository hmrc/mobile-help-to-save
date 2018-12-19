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

import com.fasterxml.jackson.core.JsonParseException
import io.lemonlabs.uri.dsl._
import org.joda.time.{LocalDate, YearMonth}
import play.api.LoggerLike
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveConnectorConfig
import uk.gov.hmrc.mobilehelptosave.config.SystemId.SystemId
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.play.encoding.UriPathEncoding.encodePathSegment

import scala.concurrent.{ExecutionContext, Future}

trait HelpToSaveEnrolmentStatus {
  def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]]
}

trait HelpToSaveGetAccount {
  def getAccount(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]]
}

trait HelpToSaveGetTransactions {
  def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Transactions]]
}

class HelpToSaveConnectorImpl(
  logger: LoggerLike,
  config: HelpToSaveConnectorConfig,
  http: CoreGet
)(
  implicit ec: ExecutionContext
)
  extends HelpToSaveGetTransactions
    with HelpToSaveGetAccount
    with HelpToSaveEnrolmentStatus {

  override def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] = {
    http.GET[JsValue](enrolmentStatusUrl.toString) map { json: JsValue =>
      Right((json \ "enrolled").as[Boolean])
    } recover handleEnrolmentStatusHttpErrors
  }

  override def getAccount(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
    val string = accountUrl(nino).toString
    http.GET[HelpToSaveAccount](string) map (account => Right(Some(account))) recover (mapNotFoundToNone orElse handleAccountHttpErrors)
  }

  override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Transactions]] = {
    val string = transactionsUrl(nino).toString
    http.GET[Transactions](string) map (Right(_)) recover handleTransactionsHttpErrors
  }

  private val mapNotFoundToNone: PartialFunction[Throwable, Either[ErrorInfo, Option[Nothing]]] = {
    case _: NotFoundException =>
      Right(None)
  }

  private def handleHttpAndJsonErrors[B](dataDescription: String): PartialFunction[Throwable, Either[ErrorInfo, B]] = {
    case _: NotFoundException =>
      Left(ErrorInfo.AccountNotFound)

    case e@(_: HttpException | _: Upstream4xxResponse | _: Upstream5xxResponse | _: JsValidationException | _: JsonParseException) =>
      logger.warn(s"Couldn't get $dataDescription from help-to-save service", e)
      Left(ErrorInfo.General)
  }

  private val handleEnrolmentStatusHttpErrors = handleHttpAndJsonErrors("enrolment status")
  private val handleAccountHttpErrors         = handleHttpAndJsonErrors("account")
  private val handleTransactionsHttpErrors    = handleHttpAndJsonErrors("transaction information")

  private lazy val enrolmentStatusUrl: URL = new URL(config.helpToSaveBaseUrl, "/help-to-save/enrolment-status")

  private def accountUrl(nino: Nino): URL = new URL(
    config.helpToSaveBaseUrl, s"/help-to-save/${encodePathSegment(nino.value)}/account" ? ("systemId" -> SystemId))

  private def transactionsUrl(nino: Nino): URL = new URL(
    config.helpToSaveBaseUrl, s"/help-to-save/${encodePathSegment(nino.value)}/account/transactions" ? ("systemId" -> SystemId))
}


/** Bonus term in help-to-save microservice's domain */
case class HelpToSaveBonusTerm(
  bonusEstimate: BigDecimal,
  bonusPaid: BigDecimal,
  endDate: LocalDate,
  bonusPaidOnOrAfterDate: LocalDate
)

object HelpToSaveBonusTerm {
  implicit val reads: Reads[HelpToSaveBonusTerm] = Json.reads[HelpToSaveBonusTerm]
}

/** Account in help-to-save microservice's domain */
case class HelpToSaveAccount(
  accountNumber: String,
  openedYearMonth: YearMonth,

  isClosed: Boolean,

  blocked: Blocking,

  balance: BigDecimal,

  paidInThisMonth: BigDecimal,
  canPayInThisMonth: BigDecimal,
  maximumPaidInThisMonth: BigDecimal,
  thisMonthEndDate: LocalDate,

  accountHolderForename: String,
  accountHolderSurname: String,
  accountHolderEmail: Option[String],

  bonusTerms: Seq[HelpToSaveBonusTerm],

  closureDate: Option[LocalDate],
  closingBalance: Option[BigDecimal]
)

object HelpToSaveAccount {
  implicit val jodaFormat: Format[YearMonth]        = uk.gov.hmrc.mobilehelptosave.json.Formats.JodaYearMonthFormat
  implicit val reads     : Reads[HelpToSaveAccount] = Json.reads[HelpToSaveAccount]
}
