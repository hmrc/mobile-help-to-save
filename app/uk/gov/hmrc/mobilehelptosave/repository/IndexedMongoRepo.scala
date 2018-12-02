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
  * Want to store something in Mongo uniquely indexed against a string value? Just create the model for the
  * data and subclass this handy helper.
  *
  * @param collectionName the name of the collection that is to be created and used in mongo for this repo
  * @param indexFieldName the name of the field in documents within the collection that will be used as the index. The
  *                       index itself will be named at `${indexFieldName}Idx`
  * @tparam I the type of the index values for the repo
  * @tparam V the type of the values stored in the repo
  *
  */
class IndexedMongoRepo[I, V](
  collectionName: String,
  indexFieldName: String,
  val reactiveMongo: Provider[ReactiveMongoComponent]
)(implicit ec: ExecutionContext, iFormat: Format[I], tFormat: Format[V])
  extends ReactiveRepository[V, BSONObjectID](collectionName, reactiveMongo.get().mongoConnector.db, tFormat) {

  override def indexes: Seq[Index] = Seq(
    Index(Seq(indexFieldName -> IndexType.Text), name = Some(s"${indexFieldName}Idx"), unique = true, sparse = true)
  )

  /**
    * Insert or update a document with the values from `value`. The `indexValue` will be used to check if there is
    * already a document stored against the index. If so, this function will update it, and if not a new document
    * will be inserted.
    *
    * The document itself may or may not contain a field matching the index value. If it does have such a field then
    * it's value should match the `indexValue` or bad things are likely to happen.
    */
  def set(indexValue: I, value: V): Future[Unit] = {
    findAndUpdate(
      obj(indexFieldName -> indexValue),
      obj("$set" -> Json.toJson(value)),
      upsert = true
    ).void
  }

  def get(indexValue: I): Future[Option[V]] =
    find(indexFieldName -> indexValue).map(_.headOption)

  def delete(indexValue: I): Future[Unit] =
    remove(indexFieldName -> indexValue).void
}
