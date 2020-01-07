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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.Eligibility
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

trait EligibilityRepo[F[_]] {
  def setEligibility(eligibility: Eligibility): F[Unit]

  def getEligibility(nino: Nino): F[Option[Eligibility]]
}

class MongoEligibilityRepo(
  mongo:       ReactiveMongoComponent
)(implicit ec: ExecutionContext, mongoFormats: Format[Eligibility])
    extends ReactiveRepository[Eligibility, Nino]("eligibility", mongo.mongoConnector.db, domainFormat = mongoFormats, implicitly[Format[Nino]])
    with EligibilityRepo[Future] {

  override def ensureIndexes(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[scala.Seq[scala.Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(
          Index(Seq("expireAt"                            -> IndexType.Descending), name = Some("expireAtIdx"), options = BSONDocument("expireAfterSeconds" -> 0))),
        collection.indexesManager.ensure(Index(Seq("nino" -> IndexType.Text), name       = Some("ninoIdx"), unique      = true, sparse = true))
      ))

  override def setEligibility(eligibility: Eligibility): Future[Unit] =
    insert(eligibility).void

  override def getEligibility(nino: Nino): Future[Option[Eligibility]] =
    collection.find(obj("nino" -> nino), None)(JsObjectDocumentWriter, JsObjectDocumentWriter).one[Eligibility]
}
