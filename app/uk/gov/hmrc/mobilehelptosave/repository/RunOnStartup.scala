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
import uk.gov.hmrc.mobilehelptosave.config.RunOnStartupConfig

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RunOnStartup(
                    mongoMilestonesRepo:       MongoMilestonesRepo,
                    mongoSavingsGoalEventRepo: MongoSavingsGoalEventRepo,
                    mongoPreviousBalanceRepo:  MongoPreviousBalanceRepo,
                    runOnStartupConfig:        RunOnStartupConfig
                  )(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger(this.getClass)

  if (runOnStartupConfig.runOnStartupEnabled) {
    val updateDB: Future[Unit] = for {
      _ <- mongoMilestonesRepo.updateExpireAt()
      _ <- mongoSavingsGoalEventRepo.updateExpireAt()
      _ <- mongoPreviousBalanceRepo.updateExpireAt()
    } yield (logger.info("Updated DB Sucessfully"))

    updateDB.recover {
      case e => logger.warn("UPDATE FAILED" + e)
    }
  }

}
