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

sealed trait SavingsGoalEventRecord {
  def nino: Option[Nino]
  def hashNino: Option[String]
  def date: Instant
  def expireAt: Instant
  def updateRequired: Boolean

  def toDomain(requestNino: Nino): SavingsGoalEvent
}

case class SavingsGoalSetEventRecord(
  nino: Option[Nino],
  hashNino: Option[String],
  amount: Option[Double] = None,
  date: Instant,
  name: Option[String] = None,
  expireAt: Instant,
  updateRequired: Boolean = false
) extends SavingsGoalEventRecord {
  override def toDomain(requestNino: Nino): SavingsGoalSetEvent =
    SavingsGoalSetEvent(requestNino, amount, date, name, expireAt, updateRequired)
}

case class SavingsGoalDeleteEventRecord(
  nino: Option[Nino],
  hashNino: Option[String],
  date: Instant,
  expireAt: Instant,
  updateRequired: Boolean = false
) extends SavingsGoalEventRecord {
  override def toDomain(requestNino: Nino): SavingsGoalDeleteEvent =
    SavingsGoalDeleteEvent(requestNino, date, expireAt, updateRequired)
}

object SavingsGoalEventRecord {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  val setEventFormat: OFormat[SavingsGoalSetEventRecord] = Json.format
  val deleteEventFormat: OFormat[SavingsGoalDeleteEventRecord] = Json.format

  private val typeReads: Reads[SavingsGoalEventType] = (__ \ "type").read

  implicit val format: OFormat[SavingsGoalEventRecord] = new OFormat[SavingsGoalEventRecord] {
    override def writes(record: SavingsGoalEventRecord): JsObject = record match {
      case event: SavingsGoalSetEventRecord =>
        setEventFormat.writes(event) + ("type" -> Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Set))
      case event: SavingsGoalDeleteEventRecord =>
        deleteEventFormat.writes(event) + ("type" -> Json.toJson[SavingsGoalEventType](SavingsGoalEventType.Delete))
    }

    override def reads(json: JsValue): JsResult[SavingsGoalEventRecord] =
      typeReads.reads(json) match {
        case JsSuccess(SavingsGoalEventType.Set, _)    => setEventFormat.reads(json)
        case JsSuccess(SavingsGoalEventType.Delete, _) => deleteEventFormat.reads(json)
        case error: JsError                            => error
      }
  }

  def  fromDomain(event: SavingsGoalEvent, hashNino: Option[String]): SavingsGoalEventRecord = event match {
    case SavingsGoalSetEvent(nino, amount, date, name, expireAt, updateRequired) =>
      SavingsGoalSetEventRecord(
        if (hashNino.isDefined) None else Some(nino),
        hashNino,
        amount,
        date,
        name,
        expireAt,
        updateRequired
      )
    case SavingsGoalDeleteEvent(nino, date, expireAt, updateRequired) =>
      SavingsGoalDeleteEventRecord(
        if (hashNino.isDefined) None else Some(nino),
        hashNino,
        date,
        expireAt,
        updateRequired
      )
  }
}
