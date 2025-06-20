/*
 * Copyright 2023 HM Revenue & Customs
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
import java.time.{LocalDate, YearMonth}
import com.fasterxml.jackson.core.JsonParseException
import jakarta.inject.Inject
import play.api.LoggerLike
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, JsValidationException, NotFoundException, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveConnectorConfig
import uk.gov.hmrc.mobilehelptosave.config.SystemId.SystemId
import uk.gov.hmrc.mobilehelptosave.domain.*
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

trait HelpToSaveEligibility {
  def checkEligibility()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, EligibilityCheckResponse]]
}

class HelpToSaveConnectorImpl @Inject() (
  logger: LoggerLike,
  config: HelpToSaveConnectorConfig,
  http: HttpClientV2
)(implicit ec: ExecutionContext)
    extends HelpToSaveGetTransactions
    with HelpToSaveGetAccount
    with HelpToSaveEnrolmentStatus
    with HelpToSaveEligibility {

  def enrolmentStatus()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Boolean]] =
    http.get(url"$enrolmentStatusUrl").execute.map { response =>
      Right((response.json \ "enrolled").as[Boolean])
    } recover handleEnrolmentStatusHttpErrors

  override def getAccount(
    nino: Nino
  )(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Option[HelpToSaveAccount]]] = {
    val string = accountUrl(nino).toString
    http
      .get(url"$string")
      .execute[HelpToSaveAccount]
      .map(account => Right(Some(account))) recover (mapNotFoundToNone orElse handleAccountHttpErrors)
  }

  override def getTransactions(nino: Nino)(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, Transactions]] = {
    val string = transactionsUrl(nino).toString
    http.get(url"$string").execute[Transactions].map(Right(_)) recover handleTransactionsHttpErrors
  }

  override def checkEligibility()(implicit hc: HeaderCarrier): Future[Either[ErrorInfo, EligibilityCheckResponse]] = {
    val string = eligibilityUrl.toString
    http.get(url"$string").execute[EligibilityCheckResponse].map(Right(_)) recover handleEligibilityHttpErrors
  }

  private val mapNotFoundToNone: PartialFunction[Throwable, Either[ErrorInfo, Option[Nothing]]] = { case _: NotFoundException =>
    Right(None)
  }

  private def handleHttpAndJsonErrors[B](dataDescription: String): PartialFunction[Throwable, Either[ErrorInfo, B]] = {
    case ex: UpstreamErrorResponse if ex.statusCode == 429 =>
      Left(ErrorInfo.MultipleRequests)
    case _: NotFoundException =>
      Left(ErrorInfo.AccountNotFound)
    case e @ (_: HttpException | _: UpstreamErrorResponse | _: JsValidationException | _: JsonParseException) =>
      logger.warn(s"Couldn't get $dataDescription from help-to-save service", e)
      Left(ErrorInfo.General)
  }

  private val handleEnrolmentStatusHttpErrors = handleHttpAndJsonErrors("enrolment status")
  private val handleAccountHttpErrors = handleHttpAndJsonErrors("account")
  private val handleTransactionsHttpErrors = handleHttpAndJsonErrors("transaction information")
  private val handleEligibilityHttpErrors = handleHttpAndJsonErrors("eligibility")

  private lazy val enrolmentStatusUrl: URL = new URL(config.helpToSaveBaseUrl, "/help-to-save/enrolment-status")

  private def accountUrl(nino: Nino): URL =
    new URL(config.helpToSaveBaseUrl, s"/help-to-save/${encodePathSegment(nino.value)}/account?systemId=$SystemId")

  private def transactionsUrl(nino: Nino): URL =
    new URL(config.helpToSaveBaseUrl, s"/help-to-save/${encodePathSegment(nino.value)}/account/transactions?systemId=$SystemId")

  private def eligibilityUrl: URL =
    new URL(config.helpToSaveBaseUrl, s"/help-to-save/eligibility-check")
}

/** Bonus term in help-to-save microservice's domain */
case class HelpToSaveBonusTerm(bonusEstimate: BigDecimal, bonusPaid: BigDecimal, endDate: LocalDate, bonusPaidOnOrAfterDate: LocalDate)

object HelpToSaveBonusTerm {
  implicit val reads: Reads[HelpToSaveBonusTerm] = Json.reads[HelpToSaveBonusTerm]
}

/** Account in help-to-save microservice's domain */
case class HelpToSaveAccount(accountNumber: String,
                             openedYearMonth: YearMonth,
                             isClosed: Boolean,
                             blocked: HtsBlocking,
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
                             closingBalance: Option[BigDecimal],
                             nbaAccountNumber: Option[String],
                             nbaPayee: Option[String],
                             nbaRollNumber: Option[String],
                             nbaSortCode: Option[String]
                            )

object HelpToSaveAccount {
  implicit val yearMonthFormat: Format[YearMonth] = uk.gov.hmrc.mobilehelptosave.json.Formats.YearMonthFormat
  implicit val reads: Reads[HelpToSaveAccount] = Json.reads[HelpToSaveAccount]
}
