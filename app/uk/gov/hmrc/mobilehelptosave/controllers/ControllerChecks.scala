/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.syntax.either._
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorBody, ErrorInfo, Shuttering}

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.{Failure, Success, Try}

trait ControllerChecks extends Results {

  private final val WebServerIsDown = new Status(521)

  def logger: LoggerLike

  def shuttering: Shuttering

  def withShuttering(shuttering: Shuttering)(fn: => Future[Result]): Future[Result] =
    if (shuttering.shuttered) successful(WebServerIsDown(Json.toJson(shuttering))) else fn

  def withValidNino(nino: String)(fn: Nino => Future[Result]): Future[Result] =
    HmrcNinoDefinition.regex.findFirstIn(nino) map (n => Right(Try(Nino(n)))) getOrElse {
      Left(s""""$nino" does not match NINO validation regex""")
    } match {
      case Right(Success(parsedNino)) => fn(parsedNino)
      case Right(Failure(exception)) =>
        successful(BadRequest(Json.toJson(ErrorBody("NINO_INVALID", exception.getMessage))))
      case Left(validationError) => successful(BadRequest(Json.toJson(ErrorBody("NINO_INVALID", validationError))))
    }

  def withMatchingNinos(nino: Nino)(fn: Nino => Future[Result])(implicit request: RequestWithIds[_]): Future[Result] =
    if (nino == request.nino) fn(nino)
    else {
      logger.warn(s"Attempt by ${request.nino} to access ${nino.value}'s data")
      successful(Forbidden)
    }

  def verifyingMatchingNino(ninoString: String)(fn: Nino => Future[Result])(
    implicit request:                   RequestWithIds[_]): Future[Result] =
    withShuttering(shuttering) {
      withValidNino(ninoString) { validNino =>
        withMatchingNinos(validNino) { verifiedUserNino =>
          fn(verifiedUserNino)
        }
      }
    }

  protected final val AccountNotFound = NotFound(
    Json.toJson(ErrorBody("ACCOUNT_NOT_FOUND", "No Help to Save account exists for the specified NINO")))
  private def errorHandler(errorInfo: ErrorInfo): Result = errorInfo match {
    case ErrorInfo.AccountNotFound        => AccountNotFound
    case v @ ErrorInfo.ValidationError(_) => UnprocessableEntity(Json.toJson(v))
    case ErrorInfo.General                => InternalServerError(Json.toJson(ErrorInfo.General))
  }

  /**
    * Standardise the mapping of ErrorInfo values to http responses
    */
  def handlingErrors[T](rightHandler: T => Result)(a: Either[ErrorInfo, T]): Result =
    a.bimap(errorHandler, rightHandler).merge
}
