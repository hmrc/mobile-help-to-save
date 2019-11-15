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

import java.util.UUID

import play.api.LoggerLike
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.connectors.ShutteringConnector
import uk.gov.hmrc.mobilehelptosave.domain.Shuttering
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class RequestWithIds[+A](request: Request[A], val nino: Option[Nino], val shuttered: Shuttering) extends WrappedRequest[A](request)

trait AuthorisedWithIds extends ActionBuilder[RequestWithIds, AnyContent] with ActionRefiner[Request, RequestWithIds]

class AuthorisedWithIdsImpl(
  logger:              LoggerLike,
  authConnector:       AuthConnector,
  shutteringConnector: ShutteringConnector,
  cc:                  ControllerComponents
)(
  implicit val executionContext: ExecutionContext
) extends AuthorisedWithIds
    with Results {

  override def parser: BodyParser[AnyContent] = cc.parsers.anyContent

  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

    for {
      shutteredResponse <- shutteringConnector.getShutteringStatus(UUID.randomUUID().toString)
      auth: Either[Result, Option[Nino]] <- if (shutteredResponse.shuttered) { Future.successful(Right(None)) } else { authenticateNino() }

      response = auth match {
        case Right(None) if (shutteredResponse.shuttered) => Right(new RequestWithIds(request, None, shuttered = shutteredResponse))
        case Right(None)                                  => Left(Forbidden("NINO not found"))
        case Right(Some(nino))                            => Right(new RequestWithIds(request, Some(nino), shuttered = shutteredResponse))
        case Left(result)                                 => Left(result)
        case _                                            => Left(BadRequest("Unexpected response from Nino authentication"))
      }
    } yield response
  }

  def authenticateNino()(implicit hc: HeaderCarrier): Future[Either[Result, Some[Nino]]] = {
    val predicates = ConfidenceLevel.L200
    val retrievals = Retrievals.nino

    authConnector
      .authorise(predicates, retrievals)
      .map {
        case Some(nino) =>
          Right(Some(Nino(nino)))
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
