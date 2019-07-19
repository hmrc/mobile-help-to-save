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

package uk.gov.hmrc.mobilehelptosave.repository

import cats.instances.future._
import cats.syntax.functor._
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.Milestone

import scala.concurrent.{ExecutionContext, Future}

trait MilestonesRepo[F[_]] {
  def setMilestone(milestone: Milestone): F[Unit]

  def getMilestones(nino: Nino): F[List[Milestone]]

  def markAsSeen(milestoneId: String): F[Unit]
}

class MongoMilestonesRepo(
  mongo:       ReactiveMongoComponent
)(implicit ec: ExecutionContext, mongoFormats: Format[Milestone])
    extends IndexedMongoRepo[Nino, Milestone]("milestones", "nino", unique = false, mongo = mongo)
    with MilestonesRepo[Future] {

  override def setMilestone(milestone: Milestone): Future[Unit] =
    collection
      .find(obj("nino" -> milestone.nino, "milestoneType" -> milestone.milestoneType), None)(JsObjectDocumentWriter, JsObjectDocumentWriter)
      .one[Milestone]
      .map {
        case Some(milestone) => if (milestone.isRepeatable) insert(milestone).void else ()
        case _               => insert(milestone)
      }

  override def getMilestones(nino: Nino): Future[List[Milestone]] =
    find("nino" -> Json.toJson(nino), "isSeen" -> false)

  override def markAsSeen(milestoneId: String): Future[Unit] =
    findAndUpdate(
      query  = obj("milestoneId" -> milestoneId),
      update = obj("$set" -> Json.obj("isSeen" -> true)),
      upsert = true
    ).void

}
