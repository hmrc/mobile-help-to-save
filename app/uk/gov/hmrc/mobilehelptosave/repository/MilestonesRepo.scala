/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.MongoMilestone

import scala.concurrent.{ExecutionContext, Future}

trait MilestonesRepo[F[_]] {
  def setMilestone(milestone: MongoMilestone): F[Unit]

  def getMilestones(nino: Nino): F[List[MongoMilestone]]

  def markAsSeen(
    nino:          Nino,
    milestoneType: String
  ): F[Unit]

  def clearMilestones(): F[Unit]
}

class MongoMilestonesRepo(
  mongo:        ReactiveMongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[MongoMilestone])
    extends IndexedMongoRepo[Nino, MongoMilestone]("milestones", "nino", unique = false, mongo = mongo)
    with MilestonesRepo[Future] {

  override def setMilestone(milestone: MongoMilestone): Future[Unit] =
    collection
      .find(obj("nino" -> milestone.nino, "milestone" -> milestone.milestone), None)(JsObjectDocumentWriter,
                                                                                     JsObjectDocumentWriter)
      .one[MongoMilestone]
      .map {
        case Some(m) => if (m.isRepeatable) insert(milestone).void else ()
        case _       => insert(milestone).void
      }

  override def getMilestones(nino: Nino): Future[List[MongoMilestone]] =
    find("nino" -> Json.toJson(nino), "isSeen" -> false)

  override def markAsSeen(
    nino:          Nino,
    milestoneType: String
  ): Future[Unit] =
    collection
      .update(ordered = false)
      .one(
        q     = obj("nino" -> nino, "milestoneType" -> milestoneType, "isSeen" -> false),
        u     = obj("$set" -> Json.obj("isSeen" -> true)),
        multi = true
      )
      .void

  override def clearMilestones(): Future[Unit] =
    removeAll().void

}
