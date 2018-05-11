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

import com.google.inject.{ImplementedBy, Inject}
import javax.inject.Singleton
import org.joda.time.DateTime
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[InvitationMongoRepository])
trait InvitationRepository extends TestableRepository[Invitation, InternalAuthId] {
  def countCreatedSince(dateTime: DateTime)(implicit ec: ExecutionContext): Future[Int]

  def insert(entity: Invitation)(implicit ec: ExecutionContext): Future[WriteResult]
}

@Singleton
class InvitationMongoRepository @Inject()(
  mongo: ReactiveMongoComponent,
  configuration: Configuration
) extends ReactiveRepository[Invitation, InternalAuthId](
  collectionName = "invitations" + configuration.getString("mongodb.collectionName.suffix").getOrElse(""),
  mongo = mongo.mongoConnector.db,
  domainFormat = InvitationMongoRepository.domainFormat,
  idFormat = InternalAuthId.format
) with InvitationRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("created" -> IndexType.Ascending),
      name = Some("createdIndex")
    )
  )

  override def countCreatedSince(dateTime: DateTime)(implicit ec: ExecutionContext): Future[Int] =
    collection.count(Some(Json.obj("created" -> Json.obj("$gte" -> dateTime))))
}

object InvitationMongoRepository {
  private[repos] val domainFormat = RenameIdForMongoFormat("internalAuthId", Json.format[Invitation])
}
