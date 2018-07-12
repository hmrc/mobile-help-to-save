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
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.config.HelpToSaveControllerConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveApi
import uk.gov.hmrc.mobilehelptosave.domain.{Account, ErrorBody, ErrorInfo, Transactions}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import scala.concurrent.Future._
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

@Singleton
class HelpToSaveController @Inject()
(
  logger: LoggerLike,
  helpToSaveApi: HelpToSaveApi,
  authorisedWithIds: AuthorisedWithIds,
  config: HelpToSaveControllerConfig
) extends BaseController {

  // CTO's HMRC-wide NINO regex
  private final val hmrcNinoRegex: Regex = "^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$".r
  private final val AccountNotFound = NotFound(Json.toJson(ErrorBody("ACCOUNT_NOT_FOUND", "No Help to Save account exists for the specified NINO")))
  private final val WebServerIsDown = new Status(521)

  private def withShuttering(isShuttered: Boolean)(fn: => Future[Result])(implicit request: RequestWithIds[AnyContent]): Future[Result] = {
    if (!isShuttered) fn else successful(WebServerIsDown(Json.toJson(config.shuttering)))
  }

  private def withValidNino(nino: String)(fn: Nino => Future[Result])(implicit request: RequestWithIds[AnyContent]): Future[Result] = {
    hmrcNinoRegex.findFirstIn(nino) map (n => Right(Try(Nino(n)))) getOrElse {
      Left(s""""$nino" does not match NINO validation regex""")
    } match {
      case Right(Success(parsedNino)) => fn(parsedNino)
      case Right(Failure(exception)) => successful(BadRequest(Json.toJson(ErrorBody("NINO_INVALID", exception.getMessage))))
      case Left(validationError) => successful(BadRequest(Json.toJson(ErrorBody("NINO_INVALID", validationError))))
    }
  }

  private def withMatchingNinos(nino: Nino)(fn: Nino => Future[Result])(implicit request: RequestWithIds[AnyContent]): Future[Result] = {
    if (nino == request.nino) fn(nino) else {
      logger.warn(s"Attempt by ${request.nino} to access ${nino.value}'s data")
      successful(Forbidden)
    }
  }

  def getTransactions(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    withShuttering(config.shuttering.shuttered) {
      withValidNino(ninoString) { validNino =>
        withMatchingNinos(validNino) { verifiedUserNino =>
          helpToSaveApi.getTransactions(verifiedUserNino).map {
            case Right(Some(transactions)) => Ok(Json.toJson(transactions.reverse))
            case Right(None) => AccountNotFound
            case Left(errorInfo) => InternalServerError(Json.toJson(errorInfo))
          }
        }
      }
    }
  }

  def getAccount(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    withShuttering(config.shuttering.shuttered) {
      withValidNino(ninoString) { validNino =>
        withMatchingNinos(validNino) { verifiedUserNino =>
          helpToSaveApi.getAccount(verifiedUserNino).map {
            case Right(Some(helpToSaveAccount)) => Ok(Json.toJson(Account(helpToSaveAccount)))
            case Right(None) => AccountNotFound
            case Left(errorInfo) => InternalServerError(Json.toJson(errorInfo))
          }
        }
      }
    }
  }
}
