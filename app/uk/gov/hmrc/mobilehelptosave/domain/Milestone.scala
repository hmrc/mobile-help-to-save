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

package uk.gov.hmrc.mobilehelptosave.domain
import java.time.LocalDateTime

import play.api.libs.json._
import uk.gov.hmrc.domain.Nino

case class Milestone(
  nino:                Nino,
  milestoneType:       MilestoneType,
  milestoneMessageKey: MilestoneMessageKey,
  isSeen:              Boolean = false,
  isRepeatable:        Boolean,
  generatedDate:       LocalDateTime = LocalDateTime.now())

object Milestone {
  implicit val format: OFormat[Milestone] = Json.format
}

case class Milestones(milestones: List[Milestone])

object Milestones {
  implicit val milestoneWrites: Writes[Milestone] = new Writes[Milestone] {
    override def writes(milestone: Milestone): JsObject =
      Json.obj(
        "milestoneType"    -> milestone.milestoneType,
        "milestoneTitle"   -> Json.toJson(milestone.milestoneMessageKey)(MilestoneMessageKey.messageKeyToTitleWrites),
        "milestoneMessage" -> Json.toJson(milestone.milestoneMessageKey)(MilestoneMessageKey.messageKeyToMessageWrites),
        "generatedDate"    -> milestone.generatedDate
      )
  }

  implicit val format: OFormat[Milestones] = Json.format
}

sealed trait MilestoneType

case object BalanceReached extends MilestoneType

object MilestoneType {
  implicit val format: Format[MilestoneType] = new Format[MilestoneType] {
    override def reads(json: JsValue): JsResult[MilestoneType] = json.as[String] match {
      case "BalanceReached" => JsSuccess(BalanceReached)
      case _                => JsError("Invalid milestone type")
    }
    override def writes(milestoneType: MilestoneType): JsString = JsString(milestoneType.toString)
  }
}

sealed trait MilestoneMessageKey

case object StartedSaving extends MilestoneMessageKey

object MilestoneMessageKey {
  implicit val format: Format[MilestoneMessageKey] = new Format[MilestoneMessageKey] {
    override def reads(json: JsValue): JsResult[MilestoneMessageKey] = json.as[String] match {
      case "StartedSaving" => JsSuccess(StartedSaving)
      case _               => JsError("Invalid milestone message key")
    }
    override def writes(milestoneMessageKey: MilestoneMessageKey): JsString = JsString(milestoneMessageKey.toString)
  }

  val messageKeyToTitleWrites: Writes[MilestoneMessageKey] = new Writes[MilestoneMessageKey] {
    override def writes(milestoneMessageKey: MilestoneMessageKey): JsString = milestoneMessageKey match {
      case StartedSaving => JsString("You've started saving")
    }
  }

  val messageKeyToMessageWrites: Writes[MilestoneMessageKey] = new Writes[MilestoneMessageKey] {
    override def writes(milestoneMessageKey: MilestoneMessageKey): JsString = milestoneMessageKey match {
      case StartedSaving => JsString("Well done for making your first payment.")
    }
  }
}

sealed trait MilestoneCheckResult

case object MilestoneHit extends MilestoneCheckResult
case object MilestoneNotHit extends MilestoneCheckResult
case object CouldNotCheck extends MilestoneCheckResult
