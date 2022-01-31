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

package uk.gov.hmrc.mobilehelptosave.domain
import java.time.LocalDateTime

import play.api.libs.json._
import uk.gov.hmrc.domain.Nino

import scala.language.implicitConversions

case class MongoMilestone(
  nino:           Nino,
  milestoneType:  MilestoneType,
  milestone:      Milestone,
  isSeen:         Boolean = false,
  isRepeatable:   Boolean = true,
  generatedDate:  LocalDateTime = LocalDateTime.now(),
  expireAt:       LocalDateTime = LocalDateTime.now().plusMonths(54),
  updateRequired: Boolean = false) {
  def compare(that: MilestoneType) = milestoneType.priority - that.priority
}

object MongoMilestone {
  implicit val format:                        OFormat[MongoMilestone] = Json.format
  implicit def ordering[A <: MongoMilestone]: Ordering[A]             = Ordering.by(_.milestoneType.priority)
}

case class Milestones(milestones: List[MongoMilestone])

object Milestones {

  class PriorityMilestones(milestones: List[MongoMilestone]) {

    def highestPriority: List[MongoMilestone] =
      if (milestones.isEmpty) List.empty[MongoMilestone] else List(milestones.min)
  }

  implicit def prioritise(milestones: List[MongoMilestone]) = new PriorityMilestones(milestones)

  implicit val milestoneWrites: Writes[MongoMilestone] = new Writes[MongoMilestone] {

    override def writes(m: MongoMilestone): JsObject =
      Json.obj(
        "milestoneType"    -> m.milestoneType,
        "milestoneKey"     -> m.milestone.key,
        "milestoneTitle"   -> Json.toJson(m.milestone)(Milestone.milestoneToTitleWrites),
        "milestoneMessage" -> Json.toJson(m.milestone)(Milestone.milestoneToMessageWrites)
      )
  }

  implicit val format: OFormat[Milestones] = Json.format
}

sealed trait MilestoneType extends Ordered[MilestoneType] {
  val priority: Int
  def compare(that: MilestoneType) = priority - that.priority
}

case object BonusPeriod extends MilestoneType { val priority    = 1 }
case object BonusReached extends MilestoneType { val priority   = 2 }
case object BalanceReached extends MilestoneType { val priority = 3 }

object MilestoneType {
  implicit def ordering[A <: MilestoneType]: Ordering[A] = Ordering.by(_.priority)

  implicit val format: Format[MilestoneType] = new Format[MilestoneType] {

    override def reads(json: JsValue): JsResult[MilestoneType] = json.as[String] match {
      case "BalanceReached" => JsSuccess(BalanceReached)
      case "BonusPeriod"    => JsSuccess(BonusPeriod)
      case "BonusReached"   => JsSuccess(BonusReached)
      case _                => JsError("Invalid milestone type")
    }
    override def writes(milestoneType: MilestoneType): JsString = JsString(milestoneType.toString)
  }
}

case class Milestone(
  key:    MilestoneKey,
  values: Option[Map[String, String]] = None)

object Milestone {
  implicit val format: Format[Milestone] = Json.format[Milestone]

  val milestoneToTitleWrites: Writes[Milestone] = new Writes[Milestone] {

    override def writes(milestone: Milestone): JsString = milestone match {
      case Milestone(BalanceReached1, _)                                   => JsString("You've started saving")
      case Milestone(BalanceReached100, _)                                 => JsString("You have saved your first £100")
      case Milestone(BalanceReached200, _)                                 => JsString("Well done for saving £200 so far")
      case Milestone(BalanceReached500, _)                                 => JsString("Well done")
      case Milestone(BalanceReached750, _)                                 => JsString("You have £750 saved up in your Help to Save account so far")
      case Milestone(BalanceReached1000, _)                                => JsString("Well done for saving £1,000")
      case Milestone(BalanceReached1500, _)                                => JsString("Your savings are £1,500 so far")
      case Milestone(BalanceReached2000, _)                                => JsString("Well done")
      case Milestone(BalanceReached2400, _)                                => JsString("You have £2,400 in savings")
      case Milestone(EndOfFirstBonusPeriodPositiveBonus, _)                => JsString("It's nearly the end of year 2")
      case Milestone(StartOfFinalBonusPeriodNoBonus, _)                    => JsString("Your Help to Save account is 2 years old")
      case Milestone(EndOfFinalBonusPeriodZeroBalanceNoBonus, _)           => JsString("It's nearly the end of year 4")
      case Milestone(EndOfFinalBonusPeriodZeroBalancePositiveBonus, _)     => JsString("It's nearly the end of year 4")
      case Milestone(EndOfFinalBonusPeriodPositiveBalanceNoBonus, _)       => JsString("It's nearly the end of year 4")
      case Milestone(EndOfFinalBonusPeriodPositiveBalancePositiveBonus, _) => JsString("It's nearly the end of year 4")
      case Milestone(FirstBonusEarnedMaximum, _)                           => JsString("Congratulations")
      case Milestone(FirstBonusEarned, _)                                  => JsString("Congratulations")
      case Milestone(FinalBonusEarnedMaximum, _)                           => JsString("Congratulations")
      case Milestone(FinalBonusEarned, _)                                  => JsString("Congratulations")
      case Milestone(FirstBonusReached150, _)                              => JsString("Well done!")
      case Milestone(FirstBonusReached300, _)                              => JsString("Your first bonus will be at least £300")
      case Milestone(FirstBonusReached600, _)                              => JsString("Congratulations! Maximum first bonus reached")
      case Milestone(FinalBonusReached75, _)                               => JsString("Great progress toward your final bonus")
      case Milestone(FinalBonusReached200, _)                              => JsString("You have earned £200 toward your final bonus")
      case Milestone(FinalBonusReached300, _)                              => JsString("Well done!")
      case Milestone(FinalBonusReached500, _)                              => JsString("Congratulations! You’ve reached a £500 bonus")
    }
  }

  val milestoneToMessageWrites: Writes[Milestone] = new Writes[Milestone] {

    override def writes(milestone: Milestone): JsString = milestone match {
      case Milestone(BalanceReached1, _)    => JsString("Well done for making your first payment.")
      case Milestone(BalanceReached100, _)  => JsString("That's great!")
      case Milestone(BalanceReached200, _)  => JsString("Your savings are growing.")
      case Milestone(BalanceReached500, _)  => JsString("You have saved £500 since opening your account.")
      case Milestone(BalanceReached750, _)  => JsString("That's great!")
      case Milestone(BalanceReached1000, _) => JsString("Your savings are growing.")
      case Milestone(BalanceReached1500, _) => JsString("That's great!")
      case Milestone(BalanceReached2000, _) => JsString("You have £2,000 in savings now.")
      case Milestone(BalanceReached2400, _) => JsString("You have saved the most possible with Help to Save!")
      case Milestone(EndOfFirstBonusPeriodPositiveBonus, values) =>
        JsString(
          s"Your first bonus of £${values get "bonusEstimate"} will be paid into your bank account from ${values get "bonusPaidOnOrAfterDate"}."
        )
      case Milestone(StartOfFinalBonusPeriodNoBonus, _) =>
        JsString("There are still 2 years to use your account to save and earn a tax-free bonus from the government.")
      case Milestone(EndOfFinalBonusPeriodZeroBalanceNoBonus, values) =>
        JsString(s"Your Help to Save account will be closed from ${values get "bonusPaidOnOrAfterDate"}.")
      case Milestone(EndOfFinalBonusPeriodZeroBalancePositiveBonus, values) =>
        JsString(
          s"Your final bonus of £${values get "bonusEstimate"} will be paid into your bank account by ${values get "bonusPaidOnOrAfterDate"}."
        )
      case Milestone(EndOfFinalBonusPeriodPositiveBalanceNoBonus, values) =>
        JsString(
          s"Your savings of £${values get "balance"} will be paid into your bank account by ${values get "bonusPaidOnOrAfterDate"}."
        )
      case Milestone(EndOfFinalBonusPeriodPositiveBalancePositiveBonus, values) =>
        JsString(
          s"Your savings of £${values get "balance"} and final bonus of £${values get "bonusEstimate"} will be paid into your bank account by ${values get "bonusPaidOnOrAfterDate"}."
        )
      case Milestone(FirstBonusEarnedMaximum, values) =>
        JsString(s"You earned the maximum first bonus of £${values get "bonusPaid"}.")
      case Milestone(FirstBonusEarned, values) =>
        JsString(s"You earned a £${values get "bonusPaid"} first bonus.")
      case Milestone(FinalBonusEarnedMaximum, values) =>
        JsString(s"You earned the maximum final bonus of £${values get "bonusPaid"}.")
      case Milestone(FinalBonusEarned, values) =>
        JsString(s"You earned a £${values get "bonusPaid"} final bonus.")
      case Milestone(FirstBonusReached150, _) => JsString("Your first bonus will be at least £150.")
      case Milestone(FirstBonusReached300, _) => JsString("Keep saving to increase your bonus!")
      case Milestone(FirstBonusReached600, _) => JsString("You have earned the whole £600 for your first bonus!")
      case Milestone(FinalBonusReached75, _)  => JsString("Your final bonus will be at least £75. Keep saving!")
      case Milestone(FinalBonusReached200, _) => JsString("That’s great!")
      case Milestone(FinalBonusReached300, _) => JsString("Your final bonus will be at least £300")
      case Milestone(FinalBonusReached500, _) => JsString("Keep saving to increase your final bonus!")
    }
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
case object EndOfFirstBonusPeriodPositiveBonus extends MilestoneKey
case object StartOfFinalBonusPeriodNoBonus extends MilestoneKey
case object EndOfFinalBonusPeriodZeroBalanceNoBonus extends MilestoneKey
case object EndOfFinalBonusPeriodZeroBalancePositiveBonus extends MilestoneKey
case object EndOfFinalBonusPeriodPositiveBalanceNoBonus extends MilestoneKey
case object EndOfFinalBonusPeriodPositiveBalancePositiveBonus extends MilestoneKey
case object FirstBonusEarnedMaximum extends MilestoneKey
case object FirstBonusEarned extends MilestoneKey
case object FinalBonusEarnedMaximum extends MilestoneKey
case object FinalBonusEarned extends MilestoneKey
case object FirstBonusReached150 extends MilestoneKey
case object FirstBonusReached300 extends MilestoneKey
case object FirstBonusReached600 extends MilestoneKey
case object FinalBonusReached75 extends MilestoneKey
case object FinalBonusReached200 extends MilestoneKey
case object FinalBonusReached300 extends MilestoneKey
case object FinalBonusReached500 extends MilestoneKey

object MilestoneKey {

  implicit val format: Format[MilestoneKey] = new Format[MilestoneKey] {

    override def reads(json: JsValue): JsResult[MilestoneKey] = json.as[String] match {
      case "BalanceReached1"                               => JsSuccess(BalanceReached1)
      case "BalanceReached100"                             => JsSuccess(BalanceReached100)
      case "BalanceReached200"                             => JsSuccess(BalanceReached200)
      case "BalanceReached500"                             => JsSuccess(BalanceReached500)
      case "BalanceReached750"                             => JsSuccess(BalanceReached750)
      case "BalanceReached1000"                            => JsSuccess(BalanceReached1000)
      case "BalanceReached1500"                            => JsSuccess(BalanceReached1500)
      case "BalanceReached2000"                            => JsSuccess(BalanceReached2000)
      case "BalanceReached2400"                            => JsSuccess(BalanceReached2400)
      case "EndOfFirstBonusPeriodPositiveBonus"            => JsSuccess(EndOfFirstBonusPeriodPositiveBonus)
      case "StartOfFinalBonusPeriodNoBonus"                => JsSuccess(StartOfFinalBonusPeriodNoBonus)
      case "EndOfFinalBonusPeriodZeroBalanceNoBonus"       => JsSuccess(EndOfFinalBonusPeriodZeroBalanceNoBonus)
      case "EndOfFinalBonusPeriodZeroBalancePositiveBonus" => JsSuccess(EndOfFinalBonusPeriodZeroBalancePositiveBonus)
      case "EndOfFinalBonusPeriodPositiveBalanceNoBonus"   => JsSuccess(EndOfFinalBonusPeriodPositiveBalanceNoBonus)
      case "EndOfFinalBonusPeriodPositiveBalancePositiveBonus" =>
        JsSuccess(EndOfFinalBonusPeriodPositiveBalancePositiveBonus)
      case "FirstBonusEarnedMaximum" => JsSuccess(FirstBonusEarnedMaximum)
      case "FirstBonusEarned"        => JsSuccess(FirstBonusEarned)
      case "FinalBonusEarnedMaximum" => JsSuccess(FinalBonusEarnedMaximum)
      case "FinalBonusEarned"        => JsSuccess(FinalBonusEarned)
      case "FirstBonusReached150"    => JsSuccess(FirstBonusReached150)
      case "FirstBonusReached300"    => JsSuccess(FirstBonusReached300)
      case "FirstBonusReached600"    => JsSuccess(FirstBonusReached600)
      case "FinalBonusReached75"     => JsSuccess(FinalBonusReached75)
      case "FinalBonusReached200"    => JsSuccess(FinalBonusReached200)
      case "FinalBonusReached300"    => JsSuccess(FinalBonusReached300)
      case "FinalBonusReached500"    => JsSuccess(FinalBonusReached500)
      case _                         => JsError("Invalid milestone key")
    }

    override def writes(milestoneKey: MilestoneKey): JsValue =
      JsString(milestoneKey.toString)

  }
}

sealed trait MilestoneCheckResult

case object MilestoneHit extends MilestoneCheckResult
case object MilestoneNotHit extends MilestoneCheckResult
case object CouldNotCheck extends MilestoneCheckResult
