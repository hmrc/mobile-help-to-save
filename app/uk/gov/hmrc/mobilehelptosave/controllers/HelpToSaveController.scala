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


import java.time.LocalDateTime

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.Json
import play.api.libs.json.Json._
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.config.HelpToSaveControllerConfig
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsTargetMongoModel, SavingsTargetRepo}
import uk.gov.hmrc.mobilehelptosave.services.AccountService
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.{ExecutionContext, Future}

trait HelpToSaveActions {
  def getTransactions(ninoString: String): Action[AnyContent]
  def getAccount(ninoString: String): Action[AnyContent]
  def putSavingsTarget(ninoString: String): Action[SavingsTarget]
  def deleteSavingsTarget(ninoString: String): Action[AnyContent]
}

@Singleton
class HelpToSaveController @Inject()
(
  val logger: LoggerLike,
  accountService: AccountService,
  helpToSaveGetTransactions: HelpToSaveGetTransactions,
  authorisedWithIds: AuthorisedWithIds,
  config: HelpToSaveControllerConfig,
  savingsTargetRepo: SavingsTargetRepo
) extends BaseController with ControllerChecks with HelpToSaveActions {

  private final val AccountNotFound = NotFound(Json.toJson(ErrorBody("ACCOUNT_NOT_FOUND", "No Help to Save account exists for the specified NINO")))

  def getTransactions(ninoString: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(config.shuttering, ninoString) { verifiedUserNino =>
        helpToSaveGetTransactions.getTransactions(verifiedUserNino).map {
          case Right(Some(transactions)) => Ok(Json.toJson(transactions.reverse))
          case Right(None)               => AccountNotFound
          case Left(errorInfo)           => InternalServerError(Json.toJson(errorInfo))
        }
      }
    }

  def getAccount(ninoString: String): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    verifyingMatchingNino(config.shuttering, ninoString)(fetchAccountDetails)
  }

  private def fetchAccountDetails(nino: Nino)(implicit hc: HeaderCarrier): Future[Result] = {
    for {
      target <- EitherT.liftF(fetchSavingsTarget(nino))
      account <- EitherT(accountService.account(nino))
    } yield (target, account)
  }.value.map {
    case Right((target, Some(account))) => Ok(Json.toJson(account.copy(savingsTarget = target.map(t => SavingsTarget(t.targetAmount)))))
    case Right((_, None))               => AccountNotFound
    case Left(errorInfo)                => InternalServerError(Json.toJson(errorInfo))
  }

  /**
    * If there is an error talking to mongo then this function will recover the `Future` to a `Success(None)` on
    * the basis that we don't want to stop the user seeing their other account details just because something
    * went wrong trying to look up their target. Other options are to convert the failure to an `ErrorInfo` and
    * return a `Future[Either[ErrorInfo, Option[SavingsTargetMongoModel]]]`, which would let the api call fail with
    * a meaningful error, or to expand the type returned to the api caller to give the client enough information
    * to tell the user something useful (e.g. "We can't display your target at the moment, but here's the rest of
    * your account details").
    */
  private def fetchSavingsTarget(nino: Nino)(implicit ec: ExecutionContext): Future[Option[SavingsTargetMongoModel]] =
    savingsTargetRepo.get(nino).recover {
      case t =>
        logger.warn("call to mongo to retrieve savings target failed", t)
        None
    }

  def putSavingsTarget(ninoString: String): Action[SavingsTarget] =
    authorisedWithIds.async(parse.json[SavingsTarget]) { implicit request: RequestWithIds[SavingsTarget] =>
      verifyingMatchingNino(config.shuttering, ninoString) { verifiedUserNino =>
        if (request.body.targetAmount < 1.0 || request.body.targetAmount > config.monthlySavingsLimit)
          Future.successful(UnprocessableEntity(obj("error" -> s"target amount should be in range 1 to ${config.monthlySavingsLimit}")))
        else
          savingsTargetRepo
            .put(SavingsTargetMongoModel(verifiedUserNino.nino, request.body.targetAmount, LocalDateTime.now))
            .map(_ => NoContent)
      }
    }

  def deleteSavingsTarget(ninoString: String): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(config.shuttering, ninoString) {
        savingsTargetRepo.delete(_).map(_ => NoContent)
      }
    }
}
