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

import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.{combine, set, setOnInsert, unset}
import org.mongodb.scala.model.{IndexModel, IndexOptions, UpdateOptions}
import play.api.Logger
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

  def deleteEligibility(nino: Nino): Future[Boolean]
}

class MongoEligibilityRepo(
  mongo: MongoComponent,
  config: MongoConfig,
  ninoHash: NinoHash,
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

  private val logger = Logger(getClass)

  override def setEligibility(eligibility: Eligibility): Future[Unit] =
    if (config.encryptionEnabled) {
      logger.warn("Eligibility repository write started with NINO hashing enabled")
      setHashedEligibility(eligibility)
    } else {
      logger.warn("Eligibility repository legacy write started with NINO hashing disabled")
      collection
        .insertOne(EligibilityRecord.legacy(eligibility))
        .toFuture()
        .map { result =>
          logger.warn(s"Eligibility repository legacy write completed: acknowledged=${result.wasAcknowledged()}")
        }
        .recoverWith { case error =>
          logger.warn("Eligibility repository legacy write failed", error)
          Future.failed(error)
        }
    }

  override def getEligibility(nino: Nino): Future[Option[Eligibility]] = {
    val record =
      if (config.encryptionEnabled)
        collection
          .find(equal("hashNino", ninoHash(nino)))
          .headOption()
          .flatMap {
            case found @ Some(_) =>
              logger.warn("Eligibility repository hashNino lookup hit")
              Future.successful(found)
            case None =>
              logger.warn("Eligibility repository hashNino lookup missed; trying legacy NINO lookup")
              collection.find(equal("nino", nino.nino)).headOption().flatMap {
                case Some(legacyRecord) =>
                  logger.warn("Eligibility repository legacy NINO fallback hit; starting hash migration")
                  val eligibility = legacyRecord.fromDomain(nino)
                  setHashedEligibility(eligibility).map { _ =>
                    logger.warn("Eligibility repository legacy NINO fallback migration completed")
                    Some(legacyRecord.copy(nino = None, hashNino = Some(ninoHash(nino))))
                  }
                case None =>
                  logger.warn("Eligibility repository legacy NINO fallback missed")
                  Future.successful(None)
              }
          }
      else {
        logger.warn("Eligibility repository read using legacy NINO because hashing is disabled")
        collection.find(equal("nino", nino.nino)).headOption().map { result =>
          logger.warn(s"Eligibility repository legacy NINO lookup hit=${result.isDefined}")
          result
        }
      }

    record.map(_.map(_.fromDomain(nino)))
  }

  override def deleteEligibility(nino: Nino): Future[Boolean] =
    collection
      .deleteOne(
        or(
          equal("hashNino", ninoHash(nino)),
          equal("nino", nino.nino)
        )
      )
      .toFuture()
      .map(_.getDeletedCount > 0)

  private def setHashedEligibility(eligibility: Eligibility): Future[Unit] = {
    val hashNino = ninoHash(eligibility.nino)

    collection
      .updateOne(
        filter = or(
          equal("hashNino", hashNino),
          equal("nino", eligibility.nino.nino)
        ),
        update = combine(
          set("hashNino", hashNino),
          set("eligible", eligibility.eligible),
          setOnInsert("expireAt", eligibility.expireAt),
          unset("nino")
        ),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .map { result =>
        logger.warn(
          s"Eligibility repository hashed write completed: acknowledged=${result.wasAcknowledged()}, " +
            s"matched=${result.getMatchedCount}, modified=${result.getModifiedCount}, " +
            s"upserted=${result.getUpsertedId != null}"
        )
      }
      .recoverWith { case error =>
        logger.warn("Eligibility repository hashed write failed", error)
        Future.failed(error)
      }
  }
}
