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

package uk.gov.hmrc.mobilehelptosave.controllers.test

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.config.UserServiceConfig
import uk.gov.hmrc.mobilehelptosave.domain.{Eligibility, MongoMilestone, MongoMilestoneRecord, TestEligibility, TestMilestone, TestPreviousBalance, TestSavingsGoal, TestSavingsGoalEvent}
import uk.gov.hmrc.mobilehelptosave.repository.{EligibilityRepo, MilestonesRepo, PreviousBalance, PreviousBalanceRepo, SavingsGoalDeleteEvent, SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController

import java.time.{Instant, LocalDateTime}
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future}

class TestController(
  savingsGoalEventRepo: SavingsGoalEventRepo,
  milestonesRepo: MilestonesRepo,
  previousBalanceRepo: PreviousBalanceRepo,
  eligibilityRepo: EligibilityRepo,
  config: UserServiceConfig,
  val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendBaseController {

  def clearGoalEvents(): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.clearGoalEvents().map {
      case true => Ok("Successfully cleared goal events")
      case _    => InternalServerError("Failed to clear goal events")
    }
  }

  def getGoalEvents(nino: Nino): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.getEvents(nino).map { events =>
      Ok(Json.toJson(events))
    }
  }

  def clearMilestoneData(): Action[AnyContent] = Action.async {
    for {
      _ <- previousBalanceRepo.clearPreviousBalance()
      _ <- milestonesRepo.clearMilestones()
    } yield Ok("Successfully cleared all milestone data")
  }

  def addMilestone: Action[TestMilestone] = Action.async(parse.json[TestMilestone]) { implicit request: Request[TestMilestone] =>
    milestonesRepo.setTestMilestone(request.body)
    Future successful Created("Milestone successfully created")
  }

  def addMilestones(amount: Int): Action[TestMilestone] = Action.async(parse.json[TestMilestone]) { implicit request: Request[TestMilestone] =>
    milestonesRepo.setTestMilestones(request.body, amount)
    Future successful Created("Milestones have all been successfully created")
  }

  def updateExpireAt(nino: Nino): Action[LocalDateTime] = Action.async(parse.json[LocalDateTime]) { implicit request: Request[LocalDateTime] =>
    milestonesRepo.updateExpireAt(nino, request.body)
    Future successful Created("Milestones have all been successfully updated")
  }

  def setMilestone(): Action[MongoMilestone] = Action.async(parse.json[MongoMilestone]) { implicit request: Request[MongoMilestone] =>
    milestonesRepo.setMilestone(request.body)
    Future successful Created("Milestones have all been successfully created")
  }

  def putSavingsGoal: Action[TestSavingsGoal] = Action.async(parse.json[TestSavingsGoal]) { implicit request: Request[TestSavingsGoal] =>
    savingsGoalEventRepo
      .setTestGoal(request.body.nino, request.body.goalAmount, request.body.goalName, request.body.date)
    Future successful Created("Goal successfully created")
  }

  def getEligibility(nino: Nino): Action[AnyContent] = Action.async {
    eligibilityRepo.getTestEligibilityRecord(nino).map {
      case Some(record) => Ok(Json.toJson(record))
      case None         => NotFound
    }
  }

  def getMilestones(nino: Nino): Action[AnyContent] = Action.async {
    milestonesRepo.getMilestones(nino).map { records =>
      if (records.isEmpty) NotFound else Ok(Json.toJson(records))
    }
  }

  def getAllEligibility: Action[AnyContent] = Action.async {
    eligibilityRepo.getAllTestEligibilityRecords().map { records =>
      Ok(Json.toJson(records))
    }
  }

  def setEligibility: Action[TestEligibility] = Action.async(parse.json[TestEligibility]) { implicit request: Request[TestEligibility] =>
    val expireAt = Instant.now().plus(config.eligibilityTtlDays, ChronoUnit.DAYS)

    eligibilityRepo
      .setTestEligibility(Eligibility(request.body.nino, request.body.eligible, expireAt), request.body.isHashed)
      .flatMap(_ => eligibilityRepo.getTestEligibilityRecord(request.body.nino))
      .map {
        case Some(record) => Created(Json.toJson(record))
        case None         => InternalServerError("Eligibility record was not found after writing")
      }
  }

  def deleteEligibility(nino: Nino): Action[AnyContent] = Action.async {
    eligibilityRepo.deleteEligibility(nino).map {
      case true  => NoContent
      case false => NotFound
    }
  }

  def getPreviousBalance(nino: Nino): Action[AnyContent] = Action.async {
    previousBalanceRepo.getTestPreviousBalanceRecord(nino).map {
      case Some(record) => Ok(Json.toJson(record))
      case None         => NotFound
    }
  }

  def getAllPreviousBalances: Action[AnyContent] = Action.async {
    previousBalanceRepo.getAllTestPreviousBalanceRecords().map(records => Ok(Json.toJson(records)))
  }

  def setPreviousBalance: Action[TestPreviousBalance] = Action.async(parse.json[TestPreviousBalance]) { implicit request =>
    val now = Instant.now()
    request.body.expireAtFrom(now) match {
      case Left(error) => Future.successful(BadRequest(Json.obj("message" -> error)))
      case Right(expireAt) =>
        val previousBalance = PreviousBalance(
          request.body.nino,
          request.body.previousBalance,
          now,
          expireAt
        )

        previousBalanceRepo
          .setTestPreviousBalance(previousBalance, request.body.isHashed)
          .flatMap(_ => previousBalanceRepo.getTestPreviousBalanceRecord(request.body.nino))
          .map {
            case Some(record) => Created(Json.toJson(record))
            case None         => InternalServerError("Previous balance record was not found after writing")
          }
    }
  }

  def deletePreviousBalance(nino: Nino): Action[AnyContent] = Action.async {
    previousBalanceRepo.deleteTestPreviousBalance(nino).map {
      case true  => NoContent
      case false => NotFound
    }
  }

  def getSavingsGoalEvents(nino: Nino): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.getTestGoalEventRecords(nino).map { records =>
      if (records.nonEmpty) Ok(Json.toJson(records)) else NotFound
    }
  }

  def getAllSavingsGoalEvents: Action[AnyContent] = Action.async {
    savingsGoalEventRepo.getAllTestGoalEventRecords().map(records => Ok(Json.toJson(records)))
  }

  def setSavingsGoalEvent: Action[TestSavingsGoalEvent] = Action.async(parse.json[TestSavingsGoalEvent]) { implicit request =>
    val now = Instant.now()

    val event = request.body.expireAtFrom(now).flatMap { expireAt =>
      request.body.eventType.toLowerCase match {
        case "set" =>
          Right(
            SavingsGoalSetEvent(
              request.body.nino,
              request.body.goalAmount,
              now,
              request.body.goalName,
              expireAt
            )
          )
        case "delete" => Right(SavingsGoalDeleteEvent(request.body.nino, now, expireAt))
        case _        => Left("eventType must be either 'set' or 'delete'")
      }
    }

    event match {
      case Left(error) => Future.successful(BadRequest(Json.obj("message" -> error)))
      case Right(goalEvent: SavingsGoalEvent) =>
        savingsGoalEventRepo
          .setTestGoalEvent(goalEvent, request.body.isHashed)
          .map(_ => Created)
    }
  }

  def deleteSavingsGoalEvents(nino: Nino): Action[AnyContent] = Action.async {
    savingsGoalEventRepo.deleteTestGoalEvents(nino).map {
      case true  => NoContent
      case false => NotFound
    }
  }

}
