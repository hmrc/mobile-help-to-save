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

package uk.gov.hmrc.mobilehelptosave.services

import java.time.LocalDateTime

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobilehelptosave.domain.SavingsGoal
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEvent, SavingsGoalEventRepo, SavingsGoalSetEvent}
import uk.gov.hmrc.mobilehelptosave.support.LoggerStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReportingServiceSpec
    extends WordSpec
    with Matchers
    with MockFactory
    with OneInstancePerTest
    with LoggerStub
    with FutureAwaits
    with DefaultAwaitTimeout {

  private val testConfig =
    TestReportingServiceConfig(penceInCurrentSavingsGoalsEnabled = true, currentSavingsGoalRangeCountsEnabled = true)

  private implicit val passedHc: HeaderCarrier = HeaderCarrier()

  val savingsGoalSetEvents = List(
    SavingsGoalSetEvent(Nino("AA000000B"), 50, LocalDateTime.parse("2019-01-16T10:15:30")),
    SavingsGoalSetEvent(Nino("AA000000B"), 49.5, LocalDateTime.parse("2019-01-16T15:20:35")),
    SavingsGoalSetEvent(Nino("AA000000C"), 20, LocalDateTime.parse("2019-02-13T15:15:30")),
    SavingsGoalSetEvent(Nino("AA000000C"), 20.52, LocalDateTime.parse("2019-05-25T20:00:30")),
    SavingsGoalSetEvent(Nino("AA000000D"), 15, LocalDateTime.parse("2019-05-05T10:00:00")),
    SavingsGoalSetEvent(Nino("AA000000D"), 1.55, LocalDateTime.parse("2019-05-06T10:00:00"))
  )

  val penceInCurrentSavingsGoals =
    Json.obj(
      "count"  -> 3,
      "values" -> List(20.52, 49.5, 1.55)
    )

  val currentSavingsGoalRangeCounts =
    Json.obj(
      "1.00 - 10.00"  -> 1,
      "10.01 - 20.00" -> 0,
      "20.01 - 30.00" -> 1,
      "30.01 - 40.00" -> 0,
      "40.01 - 50.00" -> 1
    )

  "getCurrentSavingsGoalsEvents" should {
    "get all current savings goal events" in {
      val fakeGoalsRepo = fakeSavingsGoalEventsRepo(savingsGoalSetEvents)

      val service =
        new ReportingService(logger, testConfig, fakeGoalsRepo)

      val expectedResult = List(
        SavingsGoalSetEvent(Nino("AA000000C"), 20.52, LocalDateTime.parse("2019-05-25T20:00:30")),
        SavingsGoalSetEvent(Nino("AA000000B"), 49.5, LocalDateTime.parse("2019-01-16T15:20:35")),
        SavingsGoalSetEvent(Nino("AA000000D"), 1.55, LocalDateTime.parse("2019-05-06T10:00:00"))
      )

      val result = await(service.getCurrentSavingsGoalsEvents())

      result shouldBe expectedResult
    }
  }

  "getPenceInCurrentSavingsGoals" should {
    "get current savings goals with pence values" in {
      val fakeGoalsRepo = fakeSavingsGoalEventsRepo(savingsGoalSetEvents)

      val service =
        new ReportingService(logger, testConfig, fakeGoalsRepo)

      val expectedResult = penceInCurrentSavingsGoals
      val result         = await(service.getPenceInCurrentSavingsGoals())

      result shouldBe expectedResult
    }
  }

  "getCurrentSavingsGoalRangeCounts" should {
    "get current savings goals counts per each range" in {
      val fakeGoalsRepo = fakeSavingsGoalEventsRepo(savingsGoalSetEvents)

      val service =
        new ReportingService(logger, testConfig, fakeGoalsRepo)

      val expectedResult = currentSavingsGoalRangeCounts
      val result         = await(service.getCurrentSavingsGoalRangeCounts())

      result shouldBe expectedResult
    }
  }

  private def fakeSavingsGoalEventsRepo(goalSetEvents: List[SavingsGoalSetEvent]): SavingsGoalEventRepo[Future] =
    new SavingsGoalEventRepo[Future] {

      override def setGoal(
        nino:   Nino,
        amount: Double,
        name:   Option[String] = None
      ): Future[Unit] = ???
      override def getEvents(nino:  Nino): Future[List[SavingsGoalEvent]] = ???
      override def deleteGoal(nino: Nino): Future[Unit]                   = ???
      override def clearGoalEvents(): Future[Boolean] = ???
      override def getGoal(nino: Nino): Future[Option[SavingsGoal]] = ???

      override def getGoalSetEvents(): Future[List[SavingsGoalSetEvent]] = Future.successful(goalSetEvents)
    }

}
