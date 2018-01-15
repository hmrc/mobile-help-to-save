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

package uk.gov.hmrc.mobilehelptosave.repos

import javax.inject.Singleton

import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadPreference
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[InvitationMongoRepository])
trait InvitationRepository {
  private type ID = InternalAuthId
  private type A = Invitation

  def findById(id: InternalAuthId, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[A]]
  def insert(entity: A)(implicit ec: ExecutionContext): Future[WriteResult]
  def removeById(id: ID, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult]

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]]

  def isDuplicateKey(e: DatabaseException): Boolean = e.code.contains(11000)
}

@Singleton
class InvitationMongoRepository @Inject()(mongo: ReactiveMongoComponent)
  extends ReactiveRepository[Invitation, InternalAuthId](
    collectionName = "invitations",
    mongo = mongo.mongoConnector.db,
    domainFormat = InvitationMongoFormat.mongoFormat,
    idFormat = InternalAuthId.format
  ) with InvitationRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("created" -> IndexType.Ascending),
      name = Some("createdIndex")
    )
  )
}

private[repos] object InvitationMongoFormat {

  private val caseClassIdPath: JsPath = JsPath \ "internalAuthId"
  private val mongoIdPath: JsPath = JsPath \ "_id"

  private def copyKey(fromPath: JsPath, toPath: JsPath) =
    JsPath.json.update(toPath.json.copyFrom(fromPath.json.pick))

  private def moveKey(fromPath: JsPath, toPath: JsPath) =
    (json: JsValue) => json.transform(copyKey(fromPath, toPath) andThen fromPath.json.prune).get

  private val mongoReads = Json.reads[Invitation].compose(JsPath.json.update(caseClassIdPath.json.copyFrom(mongoIdPath.json.pick)))
  private val mongoWrites = Json.writes[Invitation].transform(moveKey(caseClassIdPath, mongoIdPath))

  val mongoFormat: Format[Invitation] = Format(mongoReads, mongoWrites)

}
