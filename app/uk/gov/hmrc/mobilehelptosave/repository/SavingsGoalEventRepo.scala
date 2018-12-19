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

package uk.gov.hmrc.mobilehelptosave.repository

import java.time.LocalDateTime

import cats.instances.future._
import cats.syntax.functor._
import enumeratum._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal

import scala.concurrent.{ExecutionContext, Future}

sealed trait SavingsGoalEventType extends EnumEntry

object SavingsGoalEventType extends Enum[SavingsGoalEventType] with PlayLowercaseJsonEnum[SavingsGoalEventType] {
  //noinspection TypeAnnotation
  val values = findValues

  case object Delete extends SavingsGoalEventType
  case object Set extends SavingsGoalEventType
}


sealed trait SavingsGoalEvent {
  def nino: Nino
  def date: LocalDateTime
}
case class SavingsGoalSetEvent(nino: Nino, amount: Double, date: LocalDateTime) extends SavingsGoalEvent
case class SavingsGoalDeleteEvent(nino: Nino, date: LocalDateTime) extends SavingsGoalEvent

object SavingsGoalEvent {
  val setEventFormat   : OFormat[SavingsGoalSetEvent]    = Json.format
  val deleteEventFormat: OFormat[SavingsGoalDeleteEvent] = Json.format

  val typeReads: Reads[SavingsGoalEventType] = (__ \ "type").read

  implicit val format: OFormat[SavingsGoalEvent] = new OFormat[SavingsGoalEvent] {
    override def writes(o: SavingsGoalEvent): JsObject = o match {
      case ev: SavingsGoalSetEvent    => setEventFormat.writes(ev) + ("type" -> Json.toJson(SavingsGoalEventType.Set))
      case ev: SavingsGoalDeleteEvent => deleteEventFormat.writes(ev) + ("type" -> Json.toJson(SavingsGoalEventType.Delete))
    }

    override def reads(json: JsValue): JsResult[SavingsGoalEvent] = {
      typeReads.reads(json) match {
        case JsSuccess(ev, _) => readEvent(ev, json)
        case error: JsError   => error
      }
    }

    private def readEvent(ev: SavingsGoalEventType, json: JsValue): JsResult[SavingsGoalEvent] = ev match {
      case SavingsGoalEventType.Set    => setEventFormat.reads(json)
      case SavingsGoalEventType.Delete => deleteEventFormat.reads(json)
    }
  }
}

trait SavingsGoalEventRepo {
  def setGoal(nino: Nino, amount: Double): Future[Unit]
  def deleteGoal(nino: Nino): Future[Unit]
  def getGoal(nino: Nino): Future[Option[SavingsGoal]]
  def getEvents(nino: Nino): Future[List[SavingsGoalEvent]]
  def clearGoalEvents(): Future[Boolean]
}

class MongoSavingsGoalEventRepo(
  mongo: ReactiveMongoComponent
)
  (implicit ec: ExecutionContext, mongoFormats: Format[SavingsGoalEvent])
  extends IndexedMongoRepo[Nino, SavingsGoalEvent]("savingsGoalEvents", "nino", unique = false, mongo = mongo)
    with SavingsGoalEventRepo {

  override def setGoal(nino: Nino, amount: Double): Future[Unit] =
    insert(SavingsGoalSetEvent(nino, amount, LocalDateTime.now)).void

  override def deleteGoal(nino: Nino): Future[Unit] =
    insert(SavingsGoalDeleteEvent(nino, LocalDateTime.now)).void

  override def clearGoalEvents(): Future[Boolean] = {
    removeAll().map(_ => true).recover {
      case _ => false
    }
  }

  override def getEvents(nino: Nino): Future[List[SavingsGoalEvent]] =
    find("nino" -> Json.toJson(nino))

  override def getGoal(nino: Nino): Future[Option[SavingsGoal]] = {
    val query = collection.find(obj("nino" -> nino)).sort(obj("date" -> -1))
    val result: Future[Option[SavingsGoalEvent]] = query.one[SavingsGoalEvent]
    result.map {
      case None                                    => None
      case Some(_: SavingsGoalDeleteEvent)         => None
      case Some(SavingsGoalSetEvent(_, amount, _)) => Some(SavingsGoal(amount))
    }
  }
}
