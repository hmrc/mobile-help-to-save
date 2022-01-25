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
import play.api.libs.json.Json
import reactivemongo.api.commands.MultiBulkWriteResult
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._

import java.time.LocalDateTime
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RunOnStartup(
  mongoMilestonesRepo:       MongoMilestonesRepo,
  mongoSavingsGoalEventRepo: MongoSavingsGoalEventRepo,
  mongoPreviousBalanceRepo:  MongoPreviousBalanceRepo
)(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger(this.getClass)

  val updateValues: BSONDocument = BSONDocument(
    "$set" -> BSONDocument("expireAt"       -> LocalDateTime.now().plusMonths(54).toString),
    "$set" -> BSONDocument("updateRequired" -> true)
  )

  for {
    milestoneCount   <- mongoMilestonesRepo.count
    goalCount        <- mongoSavingsGoalEventRepo.count
    prevBalanceCount <- mongoPreviousBalanceRepo.count
  } yield (logger.info(
    s"\n====================== CURRENT MONGODB COLLECTION TOTALS ======================\n\nCurrent milestone collection count = $milestoneCount\nCurrent savingsGoal collection count = $goalCount\nCurrent previous balance collection count = $prevBalanceCount\n\n========================================================================================"
  ))

  for {
    updateMilestones <- updateMilestones()
  } yield ()

  private def updateMilestones(): Future[Unit] = {
    val updateBuilder: mongoMilestonesRepo.collection.UpdateBuilder = mongoMilestonesRepo.collection.update(true)
    for {
      totalDocsBefore <- mongoMilestonesRepo.count
      _ = logger.info(
        s"mongo.updateDb flag set to true. Updating MongoDB collection ${mongoMilestonesRepo.collection.name} collection containing $totalDocsBefore records.\n Expected records to update: $docsToUpdate"
      )
      indexesSuccess      <- mongoMilestonesRepo.ensureIndexes
      updateUnseenSuccess <- updateUnseenMilestones(updateBuilder)
      updateSeenSuccess   <- updateSeenMilestones(updateBuilder)
      docsAfter           <- mongoMilestonesRepo.count
      _ = logger.info(
        s"Update of ${mongoMilestonesRepo.collection.name} complete\nIndex creation success = $indexesSuccess\n Unseen milestones updated: ${updateUnseenSuccess.nModified}\n Seen milestones updated: ${updateSeenSuccess.nModified}\n Total milestones updated: ${updateUnseenSuccess.nModified + updateSeenSuccess.nModified}\n Total documents in collection now: $docsAfter (Expected: $totalDocsBefore)"
      )

    } yield ()
  }

  private def updateUnseenMilestones(
    builder: mongoMilestonesRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q     = BSONDocument("isSeen" -> false),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle)))
  }

  private def updateSeenMilestones(
    builder: mongoMilestonesRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q     = BSONDocument("isSeen" -> true),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle)))
  }

}
