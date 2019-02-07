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

import play.api.LoggerLike
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class RequestWithIds[+A](val nino: Nino, request: Request[A]) extends WrappedRequest[A](request)

trait AuthorisedWithIds extends ActionBuilder[RequestWithIds, AnyContent] with ActionRefiner[Request, RequestWithIds]

class AuthorisedWithIdsImpl(
  logger:        LoggerLike,
  authConnector: AuthConnector,
  cc:            ControllerComponents
)(
  implicit val executionContext: ExecutionContext
) extends AuthorisedWithIds
    with Results {

  override def parser: BodyParser[AnyContent] = cc.parsers.anyContent

  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

    val predicates = ConfidenceLevel.L200
    val retrievals = Retrievals.nino

    authConnector
      .authorise(predicates, retrievals)
      .map {
        case Some(nino) =>
          Right(new RequestWithIds(Nino(nino), request))
        case None =>
          logger.warn("NINO not found")
          Left(Forbidden("NINO not found"))
      }
      .recover {
        case e: NoActiveSession => Left(Unauthorized(s"Authorisation failure [${e.reason}]"))
        case e: InsufficientConfidenceLevel =>
          logger.warn("Forbidding access due to insufficient confidence level. User will see an error screen. To fix this see NGC-3381.")
          Left(Forbidden(s"Authorisation failure [${e.reason}]"))
        case e: AuthorisationException => Left(Forbidden(s"Authorisation failure [${e.reason}]"))
      }
  }
}
