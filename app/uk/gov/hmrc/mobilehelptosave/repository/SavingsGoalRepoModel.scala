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

package uk.gov.hmrc.mobilehelptosave.repository

import java.time.{Instant, LocalDateTime, ZoneOffset}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

case class SavingsGoalRepoModel(nino: Nino, amount: Double, createdAt: Instant)

object SavingsGoalRepoModel {
  implicit val reads: Reads[SavingsGoalRepoModel] = Json.reads[SavingsGoalRepoModel]
  implicit val writes: OWrites[SavingsGoalRepoModel] = Json.writes[SavingsGoalRepoModel]

  implicit val format: Format[SavingsGoalRepoModel] =
    Format(reads, writes)
}

sealed trait SavingsGoalEvent {
  def nino: Nino
  def date: Instant
}

sealed trait SavingsGoalEventType

object SavingsGoalEventType {

  case object Delete extends SavingsGoalEventType
  case object Set    extends SavingsGoalEventType

  implicit val format: Format[SavingsGoalEventType] = new Format[SavingsGoalEventType] {

    override def reads(json: JsValue): JsResult[SavingsGoalEventType] = json.as[String] match {
      case "delete" => JsSuccess(Delete)
      case "set"    => JsSuccess(Set)
      case _        => JsError("Invalid savings goal type")
    }

    override def writes(savingsGoalType: SavingsGoalEventType): JsString =
      JsString(savingsGoalType.toString.toLowerCase())
  }
}

case class SavingsGoalSetEvent(nino: Nino,
                               amount: Option[Double] = None,
                               date: Instant,
                               name: Option[String] = None,
                               expireAt: Instant = LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC),
                               updateRequired: Boolean = false
                              )
    extends SavingsGoalEvent

case class SavingsGoalDeleteEvent(nino: Nino,
                                  date: Instant,
                                  expireAt: Instant = LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC),
                                  updateRequired: Boolean = false
                                 )
    extends SavingsGoalEvent

object SavingsGoalEvent {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  val setEventFormat: OFormat[SavingsGoalSetEvent] = Json.format
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
        case error: JsError   => error
      }

    private def readEvent(
      ev: SavingsGoalEventType,
      json: JsValue
    ): JsResult[SavingsGoalEvent] = ev match {
      case SavingsGoalEventType.Set    => setEventFormat.reads(json)
      case SavingsGoalEventType.Delete => deleteEventFormat.reads(json)
    }
  }
}
