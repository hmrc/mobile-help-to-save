/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.instances.future.*
import cats.syntax.functor.*
import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.{combine, set, setOnInsert, unset}
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.config.MongoConfig
import uk.gov.hmrc.mobilehelptosave.domain.{Eligibility, EligibilityRecord}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait EligibilityRepo {
  def setEligibility(eligibility: Eligibility): Future[Unit]

  def getEligibility(nino: Nino): Future[Option[Eligibility]]
}

class MongoEligibilityRepo(
  mongo:          MongoComponent,
  config:         MongoConfig,
  ninoHash:       NinoHash,
  collectionName: String = "eligibility"
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[EligibilityRecord](
      collectionName = collectionName,
      mongoComponent = mongo,
      domainFormat   = EligibilityRecord.format,
      indexes = Seq(
        IndexModel(
          descending("expireAt"),
          IndexOptions()
            .name("expireAtIdx")
            .expireAfter(0, TimeUnit.SECONDS)
        ),
        IndexModel(ascending("nino"), IndexOptions().name("ninoIdx").unique(true).sparse(true)),
        IndexModel(ascending("hashNino"), IndexOptions().name("hashNinoIdx").unique(true).sparse(true))
      ),
      replaceIndexes = true
    )
    with EligibilityRepo {

  override def setEligibility(eligibility: Eligibility): Future[Unit] =
    if (config.encryptionEnabled) setHashedEligibility(eligibility)
    else collection.insertOne(EligibilityRecord.legacy(eligibility)).toFuture().void

  override def getEligibility(nino: Nino): Future[Option[Eligibility]] = {
    val record =
      if (config.encryptionEnabled)
        collection
          .find(equal("hashNino", ninoHash(nino)))
          .headOption()
          .flatMap {
            case found @ Some(_) => Future.successful(found)
            case None            => collection.find(equal("nino", nino.nino)).headOption()
          }
      else
        collection.find(equal("nino", nino.nino)).headOption()

    record.map(_.map(_.fromDomain(nino)))
  }

  private def setHashedEligibility(eligibility: Eligibility): Future[Unit] =
    collection
      .updateOne(
        filter = or(
          equal("hashNino", ninoHash(eligibility.nino)),
          equal("nino", eligibility.nino.nino)
        ),
        update = combine(
          set("hashNino", ninoHash(eligibility.nino)),
          set("eligible", eligibility.eligible),
          setOnInsert("expireAt", eligibility.expireAt),
          unset("nino")
        ),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .void
}