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

package uk.gov.hmrc.mobilehelptosave.repository

import play.api.Logger
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class RunOnStartup(
  mongoMilestonesRepo:       MongoMilestonesRepo,
  mongoSavingsGoalEventRepo: MongoSavingsGoalEventRepo,
  mongoPreviousBalanceRepo:  MongoPreviousBalanceRepo
)(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger(this.getClass)

  for {
    milestoneCount   <- mongoMilestonesRepo.count
    goalCount        <- mongoSavingsGoalEventRepo.count
    prevBalanceCount <- mongoPreviousBalanceRepo.count
  } yield (logger.info(
    s"\n====================== CURRENT MONGODB COLLECTION TOTALS ======================\n\nCurrent milestone collection count = $milestoneCount\nCurrent savingsGoal collection count = $goalCount\nCurrent previous balance collection count = $prevBalanceCount\n\n========================================================================================"
  ))

}
