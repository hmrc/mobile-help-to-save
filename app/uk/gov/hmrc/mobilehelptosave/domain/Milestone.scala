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
  nino:          Nino,
  milestoneType: MilestoneType,
  milestoneKey:  MilestoneKey,
  isSeen:        Boolean = false,
  isRepeatable:  Boolean = true,
  generatedDate: LocalDateTime = LocalDateTime.now())

object Milestone {
  implicit val format: OFormat[Milestone] = Json.format
}

case class Milestones(milestones: List[Milestone])

object Milestones {
  implicit val milestoneWrites: Writes[Milestone] = new Writes[Milestone] {
    override def writes(milestone: Milestone): JsObject =
      Json.obj(
        "milestoneType"    -> milestone.milestoneType,
        "milestoneKey"     -> milestone.milestoneKey,
        "milestoneTitle"   -> Json.toJson(milestone.milestoneKey)(MilestoneKey.keyToTitleWrites),
        "milestoneMessage" -> Json.toJson(milestone.milestoneKey)(MilestoneKey.keyToMessageWrites)
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

sealed trait MilestoneKey

case object BalanceReached1 extends MilestoneKey
case object BalanceReached100 extends MilestoneKey
case object BalanceReached200 extends MilestoneKey
case object BalanceReached500 extends MilestoneKey
case object BalanceReached750 extends MilestoneKey
case object BalanceReached1000 extends MilestoneKey
case object BalanceReached1500 extends MilestoneKey
case object BalanceReached2000 extends MilestoneKey
case object BalanceReached2400 extends MilestoneKey

object MilestoneKey {
  implicit val format: Format[MilestoneKey] = new Format[MilestoneKey] {
    override def reads(json: JsValue): JsResult[MilestoneKey] = json.as[String] match {
      case "BalanceReached1"      => JsSuccess(BalanceReached1)
      case "BalanceReached100"  => JsSuccess(BalanceReached100)
      case "BalanceReached200"  => JsSuccess(BalanceReached200)
      case "BalanceReached500"  => JsSuccess(BalanceReached500)
      case "BalanceReached750"  => JsSuccess(BalanceReached750)
      case "BalanceReached1000" => JsSuccess(BalanceReached1000)
      case "BalanceReached1500" => JsSuccess(BalanceReached1500)
      case "BalanceReached2000" => JsSuccess(BalanceReached2000)
      case "BalanceReached2400" => JsSuccess(BalanceReached2400)
      case _                    => JsError("Invalid milestone key")
    }
    override def writes(milestoneKey: MilestoneKey): JsString = JsString(milestoneKey.toString)
  }

  val keyToTitleWrites: Writes[MilestoneKey] = new Writes[MilestoneKey] {
    override def writes(milestoneKey: MilestoneKey): JsString = milestoneKey match {
      case BalanceReached1      => JsString("You've started saving")
      case BalanceReached100  => JsString("You have saved your first £100")
      case BalanceReached200  => JsString("Well done for saving £200 so far")
      case BalanceReached500  => JsString("Well done")
      case BalanceReached750  => JsString("You have £750 saved up in your Help to Save account so far")
      case BalanceReached1000 => JsString("Well done for saving £1,000")
      case BalanceReached1500 => JsString("Your savings are £1,500 so far")
      case BalanceReached2000 => JsString("Well done")
      case BalanceReached2400 => JsString("You have £2,400 in savings")
    }
  }

  val keyToMessageWrites: Writes[MilestoneKey] = new Writes[MilestoneKey] {
    override def writes(milestoneKey: MilestoneKey): JsString = milestoneKey match {
      case BalanceReached1      => JsString("Well done for making your first payment.")
      case BalanceReached100  => JsString("That's great!")
      case BalanceReached200  => JsString("Your savings are growing.")
      case BalanceReached500  => JsString("You have saved £500 since opening your account.")
      case BalanceReached750  => JsString("That's great!")
      case BalanceReached1000 => JsString("Your savings are growing.")
      case BalanceReached1500 => JsString("That's great!")
      case BalanceReached2000 => JsString("You have £2,000 in savings now.")
      case BalanceReached2400 => JsString("You have saved the most possible with Help to Save!")
    }
  }
}

sealed trait MilestoneCheckResult

case object MilestoneHit extends MilestoneCheckResult
case object MilestoneNotHit extends MilestoneCheckResult
case object CouldNotCheck extends MilestoneCheckResult
