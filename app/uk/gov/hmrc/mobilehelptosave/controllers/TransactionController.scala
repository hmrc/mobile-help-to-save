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

package uk.gov.hmrc.mobilehelptosave.controllers

import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.config.TransactionControllerConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveConnectorGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorBody, ErrorInfo, Transactions}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import scala.util.Try
import scala.util.matching.Regex

@Singleton
class TransactionController @Inject()
(
  logger: LoggerLike,
  helpToSaveConnector: HelpToSaveConnectorGetTransactions,
  authorisedWithIds: AuthorisedWithIds,
  config: TransactionControllerConfig
) extends BaseController {

  private final val AccountNotFound = NotFound(Json.toJson(ErrorBody("ACCOUNT_NOT_FOUND", "No Help to Save account exists for the specified NINO")))

  def getTransactions(nino: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    if (config.shuttering.shuttered) {
      Future successful ServiceUnavailable(Json.toJson(config.shuttering))
    }
    else {
      validateNino(nino).fold(
        { validationError =>
          Future successful BadRequest(Json.toJson(ErrorBody("NINO_INVALID", validationError)))
        },
        { ninoAsNino: Nino =>
          if (ninoAsNino == request.nino) {
            helpToSaveConnector.getTransactions(ninoAsNino).map { transactionsOrError: Either[ErrorInfo, Option[Transactions]] =>
              transactionsOrError.fold(
                errorInfo => InternalServerError(Json.toJson(errorInfo)),
                maybeTransactions => maybeTransactions.fold(AccountNotFound)(transactions => Ok(Json.toJson(transactions)))
              )
            }
          } else {
            logger.warn(s"Attempt by ${request.nino} to access $ninoAsNino's transactions")
            Future successful Forbidden
          }
        }
      )
    }
  }

  // CTO's HMRC-wide NINO regex
  private val hmrcNinoRegex: Regex = "^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$".r

  private def validateNino(nino: String): Either[String, Nino] =
    nino match {
      case hmrcNinoRegex(_*) =>
        Try(Nino(nino))
          .map(Right.apply)
          .recover {
            case e: IllegalArgumentException => Left(e.getMessage)
          }
          .get
      case _ =>
        Left(s""""$nino" does not match NINO validation regex""")
    }
}
