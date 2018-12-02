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

import cats.instances.future._
import cats.syntax.functor._
import javax.inject.Provider
import play.api.libs.json.Json.obj
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

/**
  * Want to store something in Mongo indexed against a string value? Just create the model for the
  * data and subclass this handy helper.
  */
class IndexedMongoRepo[I, V](
  collectionName: String,
  indexName: String,
  val reactiveMongo: Provider[ReactiveMongoComponent]
)(implicit ec: ExecutionContext, iFormat: Format[I], tFormat: Format[V])
  extends ReactiveRepository[V, BSONObjectID](collectionName, reactiveMongo.get().mongoConnector.db, tFormat) {
  override def indexes: Seq[Index] = Seq(
    Index(Seq(indexName -> IndexType.Text), name = Some(s"${indexName}Idx"), unique = true, sparse = true)
  )

  def set(indexValue: I, value: V): Future[Unit] = {
    findAndUpdate(
      obj(indexName -> indexValue),
      obj("$set" -> Json.toJson(value)),
      upsert = true
    ).void
  }

  def put(t: V): Future[Unit] =
    insert(t).void

  def get(indexValue: I): Future[Option[V]] =
    find(indexName -> indexValue).map(_.headOption)

  def delete(indexValue: I): Future[Unit] =
    remove(indexName -> indexValue).void
}
