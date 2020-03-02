/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneOffset}

import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FreeSpecLike, Matchers}
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.domain.Nino

class SavingsGoalEventTest extends FreeSpecLike with Matchers with GeneratorDrivenPropertyChecks {

  val genDateTime: Gen[LocalDateTime] =
    Gen.posNum[Long].map(LocalDateTime.ofEpochSecond(_, 0, ZoneOffset.UTC))

  val genNino: Gen[Nino] =
    for {
      prefix <- Gen.oneOf(Nino.validPrefixes)
      num    <- Gen.listOfN(6, Gen.numChar).map(_.mkString)
      suffix <- Gen.oneOf(Nino.validSuffixes)
    } yield Nino(s"$prefix$num$suffix")

  val genSetEvent: Gen[SavingsGoalSetEvent] = for {
    nino   <- genNino
    amount <- arbDouble.arbitrary
    date   <- genDateTime
    name   <- arbString.arbitrary
  } yield SavingsGoalSetEvent(nino, amount, date, Some(name))

  val genSetEventWithNoName: Gen[SavingsGoalSetEvent] = for {
    nino   <- genNino
    amount <- arbDouble.arbitrary
    date   <- genDateTime
  } yield SavingsGoalSetEvent(nino, amount, date, None)

  val genDeleteEvent: Gen[SavingsGoalDeleteEvent] = for {
    nino <- genNino
    date <- genDateTime
  } yield SavingsGoalDeleteEvent(nino, date)

  val genEvent: Gen[SavingsGoalEvent] =
    Gen.oneOf(genSetEvent, genSetEventWithNoName, genDeleteEvent)

  implicit val arbEvent: Arbitrary[SavingsGoalEvent] =
    Arbitrary(genEvent)

  "round-trip tests" in {
    forAll { savingsGoalEvent: SavingsGoalEvent =>
      Json.toJson(savingsGoalEvent).validate[SavingsGoalEvent] match {
        case JsSuccess(actual, _) => actual shouldBe savingsGoalEvent
        case JsError(errors)      => fail(s"Json Round-trip of $savingsGoalEvent failed with errors $errors")
      }
    }
  }
}
