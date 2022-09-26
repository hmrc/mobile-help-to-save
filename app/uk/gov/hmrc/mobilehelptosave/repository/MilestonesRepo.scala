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

import cats.instances.future._
import cats.syntax.functor._
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Indexes.text
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, UpdateOptions}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.MongoMilestone
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait MilestonesRepo[F[_]] {
  def setMilestone(milestone: MongoMilestone): F[Unit]

  def getMilestones(nino: Nino): F[Seq[MongoMilestone]]

  def markAsSeen(
    nino:          Nino,
    milestoneType: String
  ): F[Unit]

  def clearMilestones(): F[Unit]
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
                                                               .name("expireAtIdx")),
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
        update = combine(set("isSeen", true), set("expireAt", LocalDateTime.now().plusMonths(6).toString))
      )
      .toFutureOption()
      .void

  override def clearMilestones(): Future[Unit] =
    collection.deleteMany(filter = Document()).toFuture.void

}
