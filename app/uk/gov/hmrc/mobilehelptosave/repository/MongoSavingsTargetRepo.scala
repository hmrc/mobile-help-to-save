/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalDateTime

import cats.instances.future._
import cats.syntax.functor._
import javax.inject.{Inject, Provider}
import play.api.libs.json.Json._
import play.api.libs.json.{Format, Json, OWrites, Reads}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

case class SavingsTargetMongoModel(nino: String, targetAmount: Double, createdAt: LocalDateTime)

object SavingsTargetMongoModel {
  implicit val oidFormat: Format[BSONObjectID]             = ReactiveMongoFormats.objectIdFormats
  implicit val reads    : Reads[SavingsTargetMongoModel]   = Json.reads[SavingsTargetMongoModel]
  implicit val writes   : OWrites[SavingsTargetMongoModel] = Json.writes[SavingsTargetMongoModel]

  val mongoFormats: Format[SavingsTargetMongoModel] =
    ReactiveMongoFormats.mongoEntity(Format(reads, writes))
}

class MongoSavingsTargetRepo @Inject()(
  val reactiveMongo: Provider[ReactiveMongoComponent]
)
  (implicit ec: ExecutionContext)
  extends ReactiveRepository[SavingsTargetMongoModel, BSONObjectID]("savingsTargets", reactiveMongo.get().mongoConnector.db, SavingsTargetMongoModel.mongoFormats)
    with SavingsTargetRepo {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("nino" -> IndexType.Hashed), name = Some("ninoIdx"), unique = true, sparse = true)
  )

  override def put(savingsTarget: SavingsTargetMongoModel): Future[Unit] =
    insert(savingsTarget).void

  override def get(nino: Nino): Future[Option[SavingsTargetMongoModel]] =
    find("nino" -> nino.value).map(_.headOption)

  override def delete(nino: Nino): Future[Unit] =
    remove("nino" -> nino.value).void
}
