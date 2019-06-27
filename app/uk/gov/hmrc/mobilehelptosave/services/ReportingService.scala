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
import play.api.libs.json.Json
import uk.gov.hmrc.mobilehelptosave.config.ReportingServiceConfig
import uk.gov.hmrc.mobilehelptosave.domain.UserState.{apply => _}
import uk.gov.hmrc.mobilehelptosave.domain._
import uk.gov.hmrc.mobilehelptosave.repository.SavingsGoalEventRepo

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportingService(
  logger:               LoggerLike,
  config:               ReportingServiceConfig,
  savingsGoalEventRepo: SavingsGoalEventRepo[Future]
)(implicit ec:          ExecutionContext) {

  def getPenceInCurrentSavingsGoals(): Future[PenceInCurrentSavingsGoals] =
    savingsGoalEventRepo
      .getGoalSetEvents()
      .map(
        values =>
          values
            .groupBy(_.nino)
            .flatMap(
              grouped =>
                grouped._2
                  .filter(_.date == grouped._2
                    .map(_.date)
                    .max(localDateTimeOrdering))
                  .filter(!_.amount.isWhole())
                  .map(_.amount)))
      .map(p => PenceInCurrentSavingsGoals(p.size, p.toList))

  if (config.penceInCurrentSavingsGoalsEnabled) {
    getPenceInCurrentSavingsGoals().map(penceInCurrentSavingsGoals =>
      logger.info(s"Pence in current savings goals:\n${Json.prettyPrint(Json.toJson(penceInCurrentSavingsGoals))}"))
  }

}
