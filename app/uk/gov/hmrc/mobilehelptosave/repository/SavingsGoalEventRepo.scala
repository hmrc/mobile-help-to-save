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

package uk.gov.hmrc.mobilehelptosave.repository

import java.time.{LocalDate, LocalDateTime}
import cats.instances.future._
import cats.syntax.functor._
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}

import scala.concurrent.{ExecutionContext, Future}

trait SavingsGoalEventRepo[F[_]] {

  def setGoal(
    nino:                        Nino,
    amount:                      Option[Double],
    name:                        Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): F[Unit]

  def setTestGoal(
    nino:   Nino,
    amount: Option[Double],
    name:   Option[String],
    date:   LocalDate
  ): F[Unit]

  def deleteGoal(
    nino:                        Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): F[Unit]
  def getGoal(nino:   Nino): F[Option[SavingsGoal]]
  def getEvents(nino: Nino): F[List[SavingsGoalEvent]]
  def clearGoalEvents(): F[Boolean]

  def getGoalSetEvents: F[List[SavingsGoalSetEvent]]
  def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, List[SavingsGoalSetEvent]]]

  def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): F[Unit]
}

class MongoSavingsGoalEventRepo(
  mongo:        ReactiveMongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[SavingsGoalEvent])
    extends IndexedMongoRepo[Nino, SavingsGoalEvent]("savingsGoalEvents", "nino", unique = false, mongo = mongo)
    with SavingsGoalEventRepo[Future] {

  override def setGoal(
    nino:                        Nino,
    amount:                      Option[Double],
    name:                        Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    insert(
      SavingsGoalSetEvent(nino     = nino,
                          amount   = amount,
                          name     = name,
                          date     = LocalDateTime.now,
                          expireAt = secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay())
    ).void

  override def setTestGoal(
    nino:   Nino,
    amount: Option[Double],
    name:   Option[String],
    date:   LocalDate
  ): Future[Unit] =
    insert(
      SavingsGoalSetEvent(nino     = nino,
                          amount   = amount,
                          name     = name,
                          date     = date.atStartOfDay(),
                          expireAt = date.plusMonths(1).atStartOfDay())
    ).void

  override def deleteGoal(
    nino:                        Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    insert(SavingsGoalDeleteEvent(nino, LocalDateTime.now, secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay())).void

  override def clearGoalEvents(): Future[Boolean] =
    removeAll().map(_ => true).recover {
      case _ => false
    }

  override def getEvents(nino: Nino): Future[List[SavingsGoalEvent]] =
    find("nino" -> Json.toJson(nino))

  override def getGoal(nino: Nino): Future[Option[SavingsGoal]] = {
    val query =
      collection.find(obj("nino" -> nino), None)(JsObjectDocumentWriter, JsObjectDocumentWriter).sort(obj("date" -> -1))
    val result: Future[Option[SavingsGoalEvent]] = query.one[SavingsGoalEvent]
    result.map {
      case None => None
      case Some(_: SavingsGoalDeleteEvent) => None
      case Some(SavingsGoalSetEvent(_, amount, _, name, _, _)) =>
        Some(SavingsGoal(goalName = name, goalAmount = amount))
    }
  }

  override def getGoalSetEvents(): Future[List[SavingsGoalSetEvent]] =
    find("type" -> "set").map(
      _.map {
        case event: SavingsGoalSetEvent => event
        case _ => throw new IllegalStateException("Event must be a set event")
      }
    )

  override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, List[SavingsGoalSetEvent]]] =
    find("type" -> "set", "nino" -> Json.toJson(nino))
      .map(
        _.map {
          case event: SavingsGoalSetEvent => event
          case _ => throw new IllegalStateException("Event must be a set event")
        }
      )
      .map(Right(_))

  override def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): Future[Unit] = {
    val builder: collection.UpdateBuilder = collection.update(true)
    val updates = builder.element(
      q     = BSONDocument("nino" -> nino.nino, "updateRequired" -> true),
      u     = BSONDocument("$set" -> BSONDocument("expireAt" -> expireAt.toString, "updateRequired" -> false)),
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle)).void)

  }

}
