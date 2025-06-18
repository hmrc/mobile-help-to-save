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

import cats.instances.future._
import cats.syntax.functor._
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{MongoMilestone, TestMilestone}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}


import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait MilestonesRepo[F[_]] {
  def setMilestone(milestone: MongoMilestone): F[Unit]

  def setTestMilestone(milestone: TestMilestone): F[Unit]

  def setTestMilestones(milestone: TestMilestone, amount: Int): F[Unit]

  def getMilestones(nino: Nino): F[Seq[MongoMilestone]]

  def markAsSeen(
    nino:          Nino,
    milestoneType: String
  ): F[Unit]

  def clearMilestones(): F[Unit]

  def updateExpireAt(): F[Unit]

  def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): F[Unit]
}

class MongoMilestonesRepo(
  mongo:        MongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[MongoMilestone])
    extends PlayMongoRepository[MongoMilestone](collectionName = "milestones",
                                                mongoComponent = mongo,
                                                domainFormat   = mongoFormats,
                                                indexes = Seq(
                                                  IndexModel(descending("expireAt"),
                                                             IndexOptions()
                                                               .name("expireAtIdx")
                                                               .expireAfter(0, TimeUnit.SECONDS)),
                                                  IndexModel(ascending("nino"),
                                                             IndexOptions().name("ninoIdx").unique(false).sparse(true))
                                                ),
                                                replaceIndexes = true)
    with MilestonesRepo[Future] {

  override def setMilestone(milestone: MongoMilestone): Future[Unit] =
    collection
      .find(and(equal("nino", milestone.nino.nino), equal("milestone", Codecs.toBson(milestone.milestone))))
      .headOption()
      .map {
        case Some(m) => if (m.isRepeatable) collection.insertOne(milestone).toFuture().void else ()
        case _       => collection.insertOne(milestone).toFuture().void
      }

  override def getMilestones(nino: Nino): Future[Seq[MongoMilestone]] =
    collection
      .find(and(equal("nino", nino.nino), equal("isSeen", false)))
      .toFuture()

  override def markAsSeen(
    nino:          Nino,
    milestoneType: String
  ): Future[Unit] =
    collection
      .findOneAndUpdate(
        filter = and(equal("nino", nino.nino), equal("milestoneType", milestoneType), equal("isSeen", false)),
        update = combine(set("isSeen", true), set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(6)))
      )
      .toFutureOption()
      .void

  override def clearMilestones(): Future[Unit] =
    collection.deleteMany(filter = Document()).toFuture().void

  override def updateExpireAt(): Future[Unit] =
    collection
      .updateMany(
        filter = Document(),
        update = combine(set("updateRequired", true),
                         set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(54)),
                         set("generatedDate", LocalDateTime.now(ZoneOffset.UTC)))
      )
      .toFutureOption()
      .void

  override def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): Future[Unit] =
    collection
      .updateMany(
        filter = and(equal("nino", Codecs.toBson(nino)), equal("updateRequired", true)),
        update = combine(set("updateRequired", false), set("expireAt", expireAt))
      )
      .toFutureOption()
      .void

  override def setTestMilestone(milestone: TestMilestone): Future[Unit] =
    collection.insertOne(
      MongoMilestone(
          nino = milestone.nino,
          milestoneType = milestone.milestoneType,
          milestone = milestone.milestone,
          isSeen = milestone.isSeen,
          isRepeatable = milestone.isRepeatable,
          generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
          expireAt = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
      )
    ).toFuture().void

  override def setTestMilestones(milestone: TestMilestone, amount: Int): Future[Unit] =
    collection.insertMany(Array.fill(amount) {
      MongoMilestone(
        nino = Nino("AA" + "%06d".format(Random.nextInt(100000)) + "ABCD".charAt(Random.nextInt(4))),
        milestoneType = milestone.milestoneType,
        milestone = milestone.milestone,
        isSeen = milestone.isSeen,
        isRepeatable = milestone.isRepeatable,
        generatedDate = milestone.generatedDate.getOrElse(Instant.now()),
        expireAt = milestone.expireAt.getOrElse(Instant.now().plus(1, ChronoUnit.HOURS))
      )
    }).toFuture().void
}
