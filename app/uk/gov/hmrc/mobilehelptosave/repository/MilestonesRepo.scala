/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.instances.future.*
import cats.syntax.functor.*
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{MongoMilestone, MongoMilestoneRecord, TestMilestone}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mobilehelptosave.config.MongoConfig

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait MilestonesRepo {
  def setMilestone(milestone: MongoMilestone): Future[Unit]

  def setTestMilestone(milestone: TestMilestone): Future[Unit]

  def setTestMilestones(milestone: TestMilestone, amount: Int): Future[Unit]

  def getMilestones(nino: Nino): Future[Seq[MongoMilestone]]

  def markAsSeen(
    nino: Nino,
    milestoneType: String
  ): Future[Unit]

  def clearMilestones(): Future[Unit]

  def updateExpireAt(
    nino: Nino,
    expireAt: LocalDateTime
  ): Future[Unit]
}

class MongoMilestonesRepo(
  mongo: MongoComponent,
  ninoHash: NinoHash,
  config: MongoConfig
)(implicit ec: ExecutionContext, mongoFormats: Format[MongoMilestoneRecord])
    extends PlayMongoRepository[MongoMilestoneRecord](
      collectionName = "milestones",
      mongoComponent = mongo,
      domainFormat   = mongoFormats,
      indexes = Seq(
        IndexModel(descending("expireAt"),
                   IndexOptions()
                     .name("expireAtIdx")
                     .expireAfter(0, TimeUnit.SECONDS)
                  ),
        IndexModel(ascending("nino"), IndexOptions().name("ninoIdx").unique(false).sparse(true)),
        IndexModel(ascending("hashNino"), IndexOptions().name("hashNinoIdx").unique(false).sparse(true))
      ),
      replaceIndexes = true
    )
    with MilestonesRepo {

  private def insertMilestone(updatedMilestone: MongoMilestoneRecord): Future[Unit] = {
    collection.insertOne(updatedMilestone).toFuture().void
  }

  private def upsertMileStone(updatedMilestone: MongoMilestoneRecord, hashNinoString: String, nino: Nino): Future[Unit] = {

    collection
      .updateMany(
        filter = and(equal("nino", nino.nino), equal("milestone", Codecs.toBson(updatedMilestone.milestone))),
        update = combine(
          set("hashNino", hashNinoString),
          unset("nino")
        )
      )
      .toFuture()
      .void

  }

  override def setMilestone(milestone: MongoMilestone): Future[Unit] = {
    if (config.encryptionEnabled) { // if encryption is enabled, we need to check if the record exists with nino or hashNino
      val hashNinoString = ninoHash(milestone.nino)
      val updatedMilestone = milestone.toMongoMilestoneRecord(Some(hashNinoString))
      collection
        .find(
          and(
            or(
              equal("nino", milestone.nino.nino),
              equal("hashNino", hashNinoString)
            ),
            equal("milestone", Codecs.toBson(milestone.milestone))
          )
        )
        .headOption()
        .flatMap {
          case Some(m) =>
            if (m.isRepeatable) {
              insertMilestone(updatedMilestone.copy(nino = None)).flatMap(_ => upsertMileStone(updatedMilestone, hashNinoString, milestone.nino))
            } else {
              upsertMileStone(updatedMilestone, hashNinoString, milestone.nino)
            } // if record found, insert the new hashNino record only if isRepeatable is true
          // and convert the existing records with nino text to hashNino
          case _ =>
            insertMilestone(updatedMilestone.copy(nino = None)) // if record not found, insert the new record with hashNino
        }

    } else {
      // If encryption is not enabled, we can insert the record with simple nino text
      val updatedMilestone = milestone.toMongoMilestoneRecord(None)
      collection
        .find(and(equal("nino", milestone.nino.nino), equal("milestone", Codecs.toBson(milestone.milestone))))
        .headOption()
        .flatMap {
          case Some(m) => if (m.isRepeatable) insertMilestone(updatedMilestone) else Future.successful(())
          case _       => insertMilestone(updatedMilestone)
        }
    }

  }

  override def getMilestones(nino: Nino): Future[Seq[MongoMilestone]] = {
    getMileStoneRecord(nino).map(_.map(_.toMongoMilestone(nino)))
  }

  private def getMileStoneRecord(nino: Nino): Future[Seq[MongoMilestoneRecord]] = {
    if (config.encryptionEnabled) { // If encryption is enabled, we need to check if the record exists with nino or hashNino and isSeen as false
      val ninohash = ninoHash(nino)
      collection
        .find(
          and(
            or(
              equal("nino", nino.nino),
              equal("hashNino", ninoHash(nino))
            ),
            equal("isSeen", false)
          )
        )
        .toFuture()
        .flatMap { record =>
          if (record.nonEmpty) { // if found, update all the records with nino text to hashNino and remove the nino field
            collection
              .updateMany(
                filter = and(equal("nino", nino.nino), equal("isSeen", false)),
                update = combine(
                  set("hashNino", ninoHash(nino)),
                  unset("nino")
                )
              )
              .toFuture()
              .map(_ => record)
          } else { // If not found, do nothing
            Future.successful(record)
          }
          // return the record fetched from the database and add hashNino
        }

    } else { // If encryption is not enabled, fetch the record(Seq[MongoMilestoneRecord]) with simple nino text

      collection
        .find(and(equal("nino", nino.nino), equal("isSeen", false)))
        .toFuture()
    }
  }

  override def markAsSeen(
    nino: Nino,
    milestoneType: String
  ): Future[Unit] = {
    if (config.encryptionEnabled) { // if encryption is enabled,  check if the record exists with nino or hashNino and isSeen as false and milestoneType as needed
      collection
        .findOneAndUpdate(
          filter = and(or(
                         equal("nino", nino.nino),
                         equal("hashNino", ninoHash(nino))
                       ),
                       equal("milestoneType", milestoneType),
                       equal("isSeen", false)
                      ),
          update = combine(set("isSeen", true),
                           set("hashNino", ninoHash(nino)),
                           set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(6)),
                           unset("nino")
                          ) // if found, mark as seen and update expireAt and hashNino and unset the nino
        )
        .toFutureOption()
        .void
    } else { // If encryption is not enabled, find the record with simple nino text and mark as seen and milestoneType as needed
      collection
        .findOneAndUpdate(
          filter = and(equal("nino", nino.nino), equal("milestoneType", milestoneType), equal("isSeen", false)),
          update = combine(set("isSeen", true),
                           set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(6))
                          ) // if found, set it mark as seen and update expireAt date time
        )
        .toFutureOption()
        .void
    }

  }

  override def clearMilestones(): Future[Unit] =
    collection.deleteMany(filter = Document()).toFuture().void

  override def updateExpireAt(
    nino: Nino,
    expireAt: LocalDateTime
  ): Future[Unit] = {
    if (config.encryptionEnabled) { // If encryption is enabled, check if the record exists with nino or hashNino and updateRequired as true
      collection
        .updateMany(
          filter = and(or(
                         equal("nino", nino.nino),
                         equal("hashNino", ninoHash(nino))
                       ),
                       equal("updateRequired", true)
                      ),
          update =
            combine(set("updateRequired", false),
                    set("hashNino", ninoHash(nino)),
                    set("expireAt", expireAt),
                    unset("nino")
                   ) // If found, update the record with nino text to hashNino and unset the nino and set expireAt date and update required as false
        )
        .toFutureOption()
        .void
    } else {
      collection
        .updateMany(
          filter = and(equal("nino", nino.nino), equal("updateRequired", true)),
          update = combine(set("updateRequired", false), set("expireAt", expireAt))
        )
        .toFutureOption()
        .void
    }

  }

  private def selectByNino(nino: Nino): Future[Seq[MongoMilestoneRecord]] = {
    if (config.encryptionEnabled) {
      collection
        .find(
          or(
            equal("hashNino", ninoHash(nino)),
            equal("nino", nino.nino)
          )
        )
        .toFuture()

    } else {
      collection
        .find(equal("nino", nino.nino))
        .toFuture()
    }
  }

  private def deleteMany(nino: Nino): Future[Boolean] = {
    collection
      .deleteMany(
        or(
          equal("hashNino", ninoHash(nino)),
          equal("nino", nino.nino)
        )
      )
      .toFuture()
      .map(_.getDeletedCount > 0)
      .recover { case _ =>
        false
      }
  }

  override def setTestMilestone(milestone: TestMilestone): Future[Unit] = {
    for {
      milestoneData <- selectByNino(milestone.nino)
      res           <- if (milestoneData.nonEmpty) deleteMany(milestone.nino) else Future.successful(true)
    } yield {}
    if (config.encryptionEnabled) {

      collection
        .insertOne(
          MongoMilestoneRecord(
            nino          = Some(milestone.nino),
            hashNino      = Some(ninoHash(milestone.nino)), // It's a testOnly data to adding both nino and hashNino for tester's help
            milestoneType = milestone.milestoneType,
            milestone     = milestone.milestone,
            isSeen        = milestone.isSeen,
            isRepeatable  = milestone.isRepeatable,
            generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
            expireAt      = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
          )
        )
        .toFuture()
        .void
    } else {
      collection
        .insertOne(
          MongoMilestoneRecord(
            nino          = Some(milestone.nino),
            hashNino      = None,
            milestoneType = milestone.milestoneType,
            milestone     = milestone.milestone,
            isSeen        = milestone.isSeen,
            isRepeatable  = milestone.isRepeatable,
            generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
            expireAt      = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
          )
        )
        .toFuture()
        .void
    }

  }

  override def setTestMilestones(milestone: TestMilestone, amount: Int): Future[Unit] = {
    if (config.encryptionEnabled) {

      collection
        .insertMany(Seq.fill(amount) {
          val nino = Nino("AA" + "%06d".format(Random.nextInt(100000)) + "ABCD".charAt(Random.nextInt(4)))
          val hashNino = ninoHash(nino)
          MongoMilestoneRecord(
            nino          = Some(nino), // It's a testOnly data to adding both nino and hashNino for tester's help
            hashNino      = Some(hashNino),
            milestoneType = milestone.milestoneType,
            milestone     = milestone.milestone,
            isSeen        = milestone.isSeen,
            isRepeatable  = milestone.isRepeatable,
            generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
            expireAt      = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
          )
        })
        .toFuture()
        .void
    } else {
      collection
        .insertMany(Seq.fill(amount) {
          MongoMilestoneRecord(
            nino          = Some(Nino("AA" + "%06d".format(Random.nextInt(100000)) + "ABCD".charAt(Random.nextInt(4)))),
            hashNino      = None,
            milestoneType = milestone.milestoneType,
            milestone     = milestone.milestone,
            isSeen        = milestone.isSeen,
            isRepeatable  = milestone.isRepeatable,
            generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
            expireAt      = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
          )
        })
        .toFuture()
        .void
    }

  }
}
