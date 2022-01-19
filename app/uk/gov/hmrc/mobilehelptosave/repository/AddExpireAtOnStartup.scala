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

import com.google.inject.AbstractModule
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.commands.MultiBulkWriteResult
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONBatchCommands.FindAndModifyCommand

import java.time.LocalDateTime
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddExpireAtOnStartup(mongoMilestonesRepo: MongoMilestonesRepo)(implicit executionContext: ExecutionContext) {
  val logger: Logger = Logger(this.getClass)

  for {
    updateMilestones <- updateMilestones()
  } yield ()

  private def updateUnseenMilestones(): Future[MultiBulkWriteResult] = {
    val updateBuilder = mongoMilestonesRepo.collection.update(true)
    val updates = updateBuilder.element(
      q     = BSONDocument("isSeen" -> false),
      u     = BSONDocument("$set" -> BSONDocument("expireAt" -> LocalDateTime.now().plusYears(4).toString)),
      multi = true
    )
    updates.flatMap(updateEle => updateBuilder.many(Seq(updateEle)))
  }

  private def removeSeenMilestones(): Future[FindAndModifyCommand.FindAndModifyResult] =
    mongoMilestonesRepo.collection.findAndRemove(BSONDocument("isSeen" -> true))

  private def updateMilestones(): Future[Unit] =
    for {
      totalDocsBefore <- mongoMilestonesRepo.count
      docsToRemove    <- mongoMilestonesRepo.count(Json.obj("isSeen" -> true))
      docsToUpdate    <- mongoMilestonesRepo.count(Json.obj("isSeen" -> false))
      _ = logger.info(
        s"mongo.updateDb flag set to true. Updating MongoDB collection ${mongoMilestonesRepo.collection.name} collection containing $totalDocsBefore records.\n Expected records to remove: $docsToRemove\n Expected records to update: $docsToUpdate"
      )
      indexesSuccess <- mongoMilestonesRepo.ensureIndexes
      updateSuccess  <- updateUnseenMilestones()
      removeSuccess  <- removeSeenMilestones()
      docsAfter      <- mongoMilestonesRepo.count
      _ = logger.info(
        s"Update of ${mongoMilestonesRepo.collection.name} success = $updateSuccess\n Index creation success = $indexesSuccess\n documents updated: ${updateSuccess.nModified}\n documents remaining now: $docsAfter (Expected $docsToUpdate)"
      )

    } yield ()

}

class LoadOnStartupModule extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[AddExpireAtOnStartup]).asEagerSingleton()
}
