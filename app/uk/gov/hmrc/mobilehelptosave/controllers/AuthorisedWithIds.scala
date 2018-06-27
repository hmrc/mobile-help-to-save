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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, Verify}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Retrievals, ~}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.InternalAuthId
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class RequestWithIds[+A](val internalAuthId: InternalAuthId, val nino: Nino, request: Request[A]) extends WrappedRequest[A](request)

@ImplementedBy(classOf[AuthorisedWithIdsImpl])
trait AuthorisedWithIds extends ActionBuilder[RequestWithIds] with ActionRefiner[Request, RequestWithIds]

@Singleton
class AuthorisedWithIdsImpl @Inject() (
  logger: LoggerLike,
  authConnector: AuthConnector
) extends AuthorisedWithIds with Results {
  override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithIds[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

    val predicates = AuthProviders(GovernmentGateway, Verify) and ConfidenceLevel.L200
    val retrievals = Retrievals.internalId and Retrievals.nino

    authConnector.authorise(predicates, retrievals).map {
      case Some(internalAuthId) ~ Some(nino) =>
        Right(new RequestWithIds(InternalAuthId(internalAuthId), Nino(nino), request))
      case None ~ _ =>
        // <confluence>/display/PE/Retrievals+Reference#RetrievalsReference-internalId states
        // "always defined for Government Gateway and Verify auth providers"
        // and we have specified AuthProviders(GovernmentGateway, Verify) so internalId
        // should always be defined.
        logger.warn("Internal auth id not found")
        Left(InternalServerError("Internal id not found"))
      case _ ~ None =>
        logger.warn("NINO not found")
        Left(Forbidden("NINO not found"))
    }.recover {
      case e: NoActiveSession => Left(Unauthorized(s"Authorisation failure [${e.reason}]"))
      case e: InsufficientConfidenceLevel =>
        logger.warn("Forbidding access due to insufficient confidence level. User will see an error screen. To fix this see NGC-3381.")
        Left(Forbidden(s"Authorisation failure [${e.reason}]"))
      case e: AuthorisationException => Left(Forbidden(s"Authorisation failure [${e.reason}]"))
    }
  }
}
