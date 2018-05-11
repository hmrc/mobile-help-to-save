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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.NinoWithoutWtc
import uk.gov.hmrc.mongo.ReactiveRepository

@ImplementedBy(classOf[NinoWithoutWtcMongoRepository])
trait NinoWithoutWtcRepository extends TestableRepository[NinoWithoutWtc, Nino]

@Singleton
class NinoWithoutWtcMongoRepository @Inject()(
  mongo: ReactiveMongoComponent,
  configuration: Configuration
) extends ReactiveRepository[NinoWithoutWtc, Nino](
  collectionName = "invitations" + configuration.getString("mongodb.collectionName.suffix").getOrElse(""),
  mongo = mongo.mongoConnector.db,
  domainFormat = RenameIdForMongoFormat("nino", Json.format[NinoWithoutWtc]),
  idFormat = Format(Nino.ninoRead, Nino.ninoWrite)
) with NinoWithoutWtcRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("created" -> IndexType.Ascending),
      name = Some("createdIndex"),
      options = BSONDocument("expireAfterSeconds" -> 180L * 60L * 60L * 24L)
    )
  )
}
