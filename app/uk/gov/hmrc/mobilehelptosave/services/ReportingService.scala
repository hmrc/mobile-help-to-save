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

package uk.gov.hmrc.mobilehelptosave.services

import javax.inject.Singleton
import play.api.LoggerLike
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.mobilehelptosave.config.ReportingServiceConfig
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{apply => _}
import uk.gov.hmrc.mobilehelptosave.repository.{SavingsGoalEventRepo, SavingsGoalSetEvent}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportingService(
  logger:               LoggerLike,
  config:               ReportingServiceConfig,
  savingsGoalEventRepo: SavingsGoalEventRepo[Future]
)(implicit ec:          ExecutionContext) {

  def getCurrentSavingsGoalsEvents(): Future[List[SavingsGoalSetEvent]] =
    savingsGoalEventRepo
      .getGoalSetEvents()
      .map(
        events =>
          events
            .groupBy(_.nino)
            .flatMap(
              grouped =>
                grouped._2
                  .filter(event =>
                    event.date == grouped._2
                      .map(_.date)
                      .max(localDateTimeOrdering)))
            .toList)

  def getPenceInCurrentSavingsGoals(): Future[JsObject] =
    getCurrentSavingsGoalsEvents()
      .map(_.filter(!_.amount.isWhole()).map(_.amount))
      .map(
        p =>
          Json.obj(
            "count"  -> p.size,
            "values" -> p
        ))

  def getCurrentSavingsGoalRangeCounts(): Future[JsObject] =
    getCurrentSavingsGoalsEvents().map(
      events =>
        Json.obj(
          "1.00 - 10.00"  -> events.count(event => event.amount > 1 && event.amount <= 10),
          "10.01 - 20.00" -> events.count(event => event.amount > 10 && event.amount <= 20),
          "20.01 - 30.00" -> events.count(event => event.amount > 20 && event.amount <= 30),
          "30.01 - 40.00" -> events.count(event => event.amount > 30 && event.amount <= 40),
          "40.01 - 50.00" -> events.count(event => event.amount > 40 && event.amount <= 50)
      )
    )

  if (config.currentSavingsGoalRangeCountsEnabled) {
    getCurrentSavingsGoalRangeCounts().map(currentGoalRangeCounts =>
      logger.info(s"Current savings goal range counts:\n${Json.prettyPrint(currentGoalRangeCounts)}"))
  }

  if (config.penceInCurrentSavingsGoalsEnabled) {
    getPenceInCurrentSavingsGoals().map(penceInCurrentSavingsGoals =>
      logger.info(s"Pence in current savings goals:\n${Json.prettyPrint(Json.toJson(penceInCurrentSavingsGoals))}"))
  }

}
