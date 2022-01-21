/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.connectors.HelpToSaveGetTransactions
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEventRepo
import uk.gov.hmrc.mobilehelptosave.services.{AccountService, SavingsUpdateService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import scala.concurrent.{ExecutionContext, Future}

trait HelpToSaveActions {

  def getTransactions(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent]

  def getAccount(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent]

  def putSavingsGoal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[SavingsGoal]

  def deleteSavingsGoal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent]

  def getSavingsUpdate(journeyId: JourneyId): Action[AnyContent]
}

class HelpToSaveController(
  val logger:                LoggerLike,
  accountService:            AccountService[Future],
  helpToSaveGetTransactions: HelpToSaveGetTransactions[Future],
  authorisedWithIds:         AuthorisedWithIds,
  savingsUpdateService:      SavingsUpdateService,
  savingsGoalEventRepo:      SavingsGoalEventRepo[Future],
  val controllerComponents:  ControllerComponents
)(implicit ec:               ExecutionContext)
    extends BackendBaseController
    with ControllerChecks
    with HelpToSaveActions {

  private def orAccountNotFound[T: Writes](o: Option[T]): Result =
    o.fold(AccountNotFound)(v => Ok(Json.toJson(v)))

  override def getTransactions(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino, request.shuttered) { verifiedUserNino =>
        helpToSaveGetTransactions
          .getTransactions(verifiedUserNino)
          .map(handlingErrors(txs => Ok(Json.toJson(txs.reverse))))
      }
    }

  override def getAccount(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent] = authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
    verifyingMatchingNino(nino, request.shuttered) { nino =>
      //noinspection ConvertibleToMethodValue
      accountService.account(nino).map(handlingErrors(orAccountNotFound(_)))
    }
  }

  override def putSavingsGoal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[SavingsGoal] =
    authorisedWithIds.async(parse.json[SavingsGoal]) { implicit request: RequestWithIds[SavingsGoal] =>
      verifyingMatchingNino(nino, request.shuttered) { verifiedUserNino =>
        request.body match {
          case SavingsGoal(None, None) => Future.successful(BadRequest("Invalid savings goal combination"))
          case _                       => accountService.setSavingsGoal(verifiedUserNino, request.body).map(handlingErrors(_ => NoContent))
        }
      }
    }

  override def deleteSavingsGoal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      verifyingMatchingNino(nino, request.shuttered) { verifiedNino =>
        accountService.deleteSavingsGoal(verifiedNino).map(handlingErrors(_ => NoContent))
      }
    }

  override def getSavingsUpdate(journeyId: JourneyId): Action[AnyContent] =
    authorisedWithIds.async { implicit request: RequestWithIds[AnyContent] =>
      if (request.nino.isEmpty) Future successful Forbidden("NINO not found")
      else {
        withShuttering(request.shuttered) {
          for {
            account       <- accountService.account(request.nino.getOrElse(Nino("")))
            accountExists <- Future successful account.toOption.map(_.getOrElse(None)).getOrElse(None) != None
            transactions <- if (account.isRight && accountExists)
                             helpToSaveGetTransactions.getTransactions(request.nino.getOrElse(Nino("")))
                           else Future successful Left(AccountNotFound)
            goalSetEvents <- if (account.isRight && accountExists)
                              savingsGoalEventRepo.getGoalSetEvents(request.nino.getOrElse(Nino("")))
                            else Future successful Left(AccountNotFound)
          } yield {
            (account, transactions, accountExists, goalSetEvents) match {
              case (Left(ErrorInfo.AccountNotFound), _, _, _) => AccountNotFound
              case (Right(_), _, false, _)                    => AccountNotFound
              case (Right(accountResult), Right(foundTransactions), true, Right(foundGoalEvents)) =>
                accountResult match {
                  case Some(accountFound) =>
                    Ok(
                      Json.toJson(
                        savingsUpdateService
                          .getSavingsUpdateResponse(accountFound, foundTransactions, foundGoalEvents)
                      )
                    )
                  case None => AccountNotFound
                }
              case (_, _, _, _) => InternalServerError(Json.toJson[ErrorInfo](ErrorInfo.General))
            }
          }
        }
      }
    }
}
