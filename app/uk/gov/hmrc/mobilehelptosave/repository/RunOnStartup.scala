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
import reactivemongo.api.commands.MultiBulkWriteResult
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mobilehelptosave.config.RunOnStartupConfig

import java.time.LocalDateTime
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RunOnStartup(
  mongoMilestonesRepo:       MongoMilestonesRepo,
  mongoSavingsGoalEventRepo: MongoSavingsGoalEventRepo,
  mongoPreviousBalanceRepo:  MongoPreviousBalanceRepo,
  config:                    RunOnStartupConfig
)(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger(this.getClass)

  val updateValues: BSONDocument = BSONDocument(
    "$set" -> BSONDocument("expireAt" -> LocalDateTime.now().plusMonths(54).toString, "updateRequired" -> true)
  )

  for {
    milestoneCount           <- mongoMilestonesRepo.count
    milestoneSeenCount       <- mongoMilestonesRepo.find("isSeen" -> true)
    milestoneUnseenCount     <- mongoMilestonesRepo.find("isSeen" -> false)
    goalCount                <- mongoSavingsGoalEventRepo.count
    goalSetCount             <- mongoSavingsGoalEventRepo.find("type" -> "set")
    goalDeleteCount          <- mongoSavingsGoalEventRepo.find("type" -> "delete")
    prevBalanceCount         <- mongoPreviousBalanceRepo.count
    prevPositiveBalanceCount <- mongoPreviousBalanceRepo.find("previousBalance" -> BSONDocument("$gt" -> 0))
    prevZeroBalanceCount     <- mongoPreviousBalanceRepo.find("previousBalance" -> 0)
  } yield (logger.info(
    s"\n====================== CURRENT MONGODB COLLECTION TOTALS ======================\n\nCurrent milestone collection count = $milestoneCount\nSeen milestones = ${milestoneSeenCount.size}\nUnseen milestones = ${milestoneUnseenCount.size}\n\nCurrent savingsGoal collection count = $goalCount\nSet savingsGoals  = ${goalSetCount.size}\nDelete savingsGoals  = ${goalDeleteCount.size}\n\nCurrent previous balance collection count = $prevBalanceCount\nPrevious positive balances = ${prevPositiveBalanceCount.size}\nPrevious zero balances = ${prevZeroBalanceCount.size}\n\n==============================================================================="
  ))

  for {
    _ <- if (config.milestonesUpdateEnabled) updateMilestones() else Future.successful()
    _ <- if (config.savingsGoalsUpdateEnabled) updateSavingsGoals() else Future.successful()
    _ <- if (config.previousBalancesUpdateEnabled) updatePreviousBalances() else Future.successful()
  } yield ()

  private def updateMilestones(): Future[Unit] = {
    val updateBuilder: mongoMilestonesRepo.collection.UpdateBuilder = mongoMilestonesRepo.collection.update(true)
    for {
      totalDocsBefore <- mongoMilestonesRepo.count
      _ = logger.info(
        s"mongo.updateMilestones flag set to true. Updating Milestones collection ${mongoMilestonesRepo.collection.name} collection containing $totalDocsBefore records.\n Expected records to update: $totalDocsBefore"
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

  private def updateSavingsGoals(): Future[Unit] = {
    val updateBuilder: mongoSavingsGoalEventRepo.collection.UpdateBuilder =
      mongoSavingsGoalEventRepo.collection.update(true)
    for {
      totalDocsBefore <- mongoSavingsGoalEventRepo.count
      _ = logger.info(
        s"mongo.updateSavingsGoals flag set to true. Updating SavingsGoals collection ${mongoSavingsGoalEventRepo.collection.name} collection containing $totalDocsBefore records.\n Expected records to update: $totalDocsBefore"
      )
      indexesSuccess           <- mongoSavingsGoalEventRepo.ensureIndexes
      updateSetGoalsSuccess    <- updateSetGoals(updateBuilder)
      updateDeleteGoalsSuccess <- updateDeleteGoals(updateBuilder)
      docsAfter                <- mongoSavingsGoalEventRepo.count
      _ = logger.info(
        s"Update of ${mongoSavingsGoalEventRepo.collection.name} complete\nIndex creation success = $indexesSuccess\n set savings goals updated: ${updateSetGoalsSuccess.nModified}\n delete savings goals updated: ${updateDeleteGoalsSuccess.nModified}\n Total savings goals updated: ${updateSetGoalsSuccess.nModified + updateDeleteGoalsSuccess.nModified}\n Total documents in collection now: $docsAfter (Expected: $totalDocsBefore)"
      )

    } yield ()
  }

  private def updateSetGoals(
    builder: mongoSavingsGoalEventRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q     = BSONDocument("type" -> "set"),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle)))
  }

  private def updateDeleteGoals(
    builder: mongoSavingsGoalEventRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q     = BSONDocument("type" -> "delete"),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle)))
  }

  private def updatePreviousBalances(): Future[Unit] = {
    val updateBuilder: mongoPreviousBalanceRepo.collection.UpdateBuilder =
      mongoPreviousBalanceRepo.collection.update(true)
    for {
      totalDocsBefore <- mongoPreviousBalanceRepo.count
      _ = logger.info(
        s"mongo.updatePreviousBalances flag set to true. Updating Previous Balance collection ${mongoPreviousBalanceRepo.collection.name} collection containing $totalDocsBefore records.\n Expected records to update: $totalDocsBefore"
      )
      indexesSuccess                   <- mongoPreviousBalanceRepo.ensureIndexes
      updatePositivePrevBalanceSuccess <- updatePositivePreviousBalances(updateBuilder)
      updateZeroPrevBalanceSuccess     <- updateZeroePreviousBalances(updateBuilder)
      docsAfter                        <- mongoPreviousBalanceRepo.count
      _ = logger.info(
        s"Update of ${mongoPreviousBalanceRepo.collection.name} complete\nIndex creation success = $indexesSuccess\n positive previous balances updated: ${updatePositivePrevBalanceSuccess.nModified}\n zero previous balances updated: ${updateZeroPrevBalanceSuccess.nModified}\n Total savings goals updated: ${updatePositivePrevBalanceSuccess.nModified + updateZeroPrevBalanceSuccess.nModified}\n Total documents in collection now: $docsAfter (Expected: $totalDocsBefore)"
      )

    } yield ()
  }

  private def updatePositivePreviousBalances(
    builder: mongoPreviousBalanceRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q = BSONDocument(
        "previousBalance" -> BSONDocument(
          "$gt" -> 0
        )
      ),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle))).recover {
      case e => {
        logger.warn("Error trying to update previousBalances: " + e)
        throw new InternalError
      }

    }
  }

  private def updateZeroePreviousBalances(
    builder: mongoPreviousBalanceRepo.collection.UpdateBuilder
  ): Future[MultiBulkWriteResult] = {

    val updates = builder.element(
      q = BSONDocument(
        "previousBalance" -> 0
      ),
      u     = updateValues,
      multi = true
    )
    updates.flatMap(updateEle => builder.many(Seq(updateEle))).recover {
      case e => {
        logger.warn("Error trying to update previousBalances: " + e)
        throw new InternalError
      }

    }
  }
}
