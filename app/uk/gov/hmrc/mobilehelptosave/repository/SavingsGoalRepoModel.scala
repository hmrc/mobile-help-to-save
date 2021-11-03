/*
 * Copyright 2021 HM Revenue & Customs
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

import enumeratum.{Enum, EnumEntry, PlayLowercaseJsonEnum}
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino

case class SavingsGoalRepoModel(
  nino:      Nino,
  amount:    Double,
  createdAt: LocalDateTime)

object SavingsGoalRepoModel {
  implicit val reads:  Reads[SavingsGoalRepoModel]   = Json.reads[SavingsGoalRepoModel]
  implicit val writes: OWrites[SavingsGoalRepoModel] = Json.writes[SavingsGoalRepoModel]

  implicit val format: Format[SavingsGoalRepoModel] =
    Format(reads, writes)
}

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

case class SavingsGoalSetEvent(
  nino:   Nino,
  amount: Option[Double] = None,
  date:   LocalDateTime,
  name:   Option[String] = None)
    extends SavingsGoalEvent

case class SavingsGoalDeleteEvent(
  nino: Nino,
  date: LocalDateTime)
    extends SavingsGoalEvent

object SavingsGoalEvent {
  val setEventFormat:    OFormat[SavingsGoalSetEvent]    = Json.format
  val deleteEventFormat: OFormat[SavingsGoalDeleteEvent] = Json.format

  val typeReads: Reads[SavingsGoalEventType] = (__ \ "type").read

  implicit val format: OFormat[SavingsGoalEvent] = new OFormat[SavingsGoalEvent] {

    override def writes(o: SavingsGoalEvent): JsObject = o match {
      case ev: SavingsGoalSetEvent =>
        setEventFormat.writes(ev) + ("type" -> Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Set))
      case ev: SavingsGoalDeleteEvent =>
        deleteEventFormat.writes(ev) + ("type" -> Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Delete))
    }

    override def reads(json: JsValue): JsResult[SavingsGoalEvent] =
      typeReads.reads(json) match {
        case JsSuccess(ev, _) => readEvent(ev, json)
        case error: JsError => error
      }

    private def readEvent(
      ev:   SavingsGoalEventType,
      json: JsValue
    ): JsResult[SavingsGoalEvent] = ev match {
      case SavingsGoalEventType.Set    => setEventFormat.reads(json)
      case SavingsGoalEventType.Delete => deleteEventFormat.reads(json)
    }
  }
}
