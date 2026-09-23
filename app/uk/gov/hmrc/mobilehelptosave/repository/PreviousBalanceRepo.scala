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
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal, or}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.{combine, set, setOnInsert, unset}
import org.mongodb.scala.model.{FindOneAndReplaceOptions, IndexModel, IndexOptions, UpdateOptions}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.config.MongoConfig
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait PreviousBalanceRepo {
  def setPreviousBalance(nino: Nino, previousBalance: BigDecimal, finalBonusPaidByDate: LocalDateTime): Future[Unit]

  def getPreviousBalance(nino: Nino): Future[Option[PreviousBalance]]

  def clearPreviousBalance(): Future[Unit]

  def updateExpireAt(): Future[Unit]

  def updateExpireAt(nino: Nino, expireAt: LocalDateTime): Future[Unit]

  def getPreviousBalanceUpdateRequired(nino: Nino): Future[Option[PreviousBalance]]

  def setTestPreviousBalance(previousBalance: PreviousBalance, isHashed: Boolean): Future[Unit]

  def getTestPreviousBalanceRecord(nino: Nino): Future[Option[PreviousBalanceRecord]]

  def getAllTestPreviousBalanceRecords(): Future[Seq[PreviousBalanceRecord]]

  def deleteTestPreviousBalance(nino: Nino): Future[Boolean]
}

class MongoPreviousBalanceRepo(
  mongo: MongoComponent,
  config: MongoConfig,
  ninoHash: NinoHash,
  collectionName: String = "previousBalance"
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[PreviousBalanceRecord](
      collectionName = collectionName,
      mongoComponent = mongo,
      domainFormat   = PreviousBalanceRecord.formats,
      indexes = Seq(
        IndexModel(
          descending("expireAt"),
          IndexOptions()
            .name("expireAtIdx")
            .expireAfter(0, TimeUnit.SECONDS)
        ),
        IndexModel(ascending("nino"), IndexOptions().name("ninoIdx").unique(false).sparse(true)),
        IndexModel(ascending("hashNino"), IndexOptions().name("hashNinoIdx").unique(false).sparse(true))
      ),
      replaceIndexes = true
    )
    with PreviousBalanceRepo {

  override def setPreviousBalance(
    nino: Nino,
    previousBalance: BigDecimal,
    finalBonusPaidByDate: LocalDateTime
  ): Future[Unit] = {
    val expireAt = finalBonusPaidByDate.plusMonths(6).toInstant(ZoneOffset.UTC)

    if (config.encryptionEnabled)
      setHashedPreviousBalance(PreviousBalance(nino, previousBalance, Instant.now(), expireAt))
    else
      collection
        .findOneAndReplace(
          filter      = equal("nino", nino.nino),
          replacement = PreviousBalanceRecord.legacy(PreviousBalance(nino, previousBalance, Instant.now(), expireAt)),
          options     = FindOneAndReplaceOptions().upsert(true)
        )
        .toFuture()
        .void
  }

  override def getPreviousBalance(nino: Nino): Future[Option[PreviousBalance]] =
    getPreviousBalanceRecord(nino).map(_.map(_.toDomain(nino)))

  override def clearPreviousBalance(): Future[Unit] =
    collection.deleteMany(filter = Document()).toFuture().void

  override def updateExpireAt(): Future[Unit] =
    collection
      .updateMany(
        filter = Document(),
        update = combine(
          set("updateRequired", true),
          set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC)),
          set("date", Instant.now())
        )
      )
      .toFutureOption()
      .void

  override def updateExpireAt(nino: Nino, expireAt: LocalDateTime): Future[Unit] = {
    val update =
      if (config.encryptionEnabled)
        combine(
          set("hashNino", ninoHash(nino)),
          unset("nino"),
          set("updateRequired", false),
          set("expireAt", expireAt.toInstant(ZoneOffset.UTC))
        )
      else combine(set("updateRequired", false), set("expireAt", expireAt.toInstant(ZoneOffset.UTC)))

    collection
      .updateMany(
        filter = and(identifierFilter(nino), equal("updateRequired", true)),
        update = update
      )
      .toFutureOption()
      .void
  }

  override def getPreviousBalanceUpdateRequired(nino: Nino): Future[Option[PreviousBalance]] =
    getPreviousBalanceRecord(nino, Some(equal("updateRequired", true))).map(_.map(_.toDomain(nino)))

  override def setTestPreviousBalance(previousBalance: PreviousBalance, isHashed: Boolean): Future[Unit] = {
    val hashNino = ninoHash(previousBalance.nino)
    val identifierUpdate =
      if (isHashed) combine(set("hashNino", hashNino), unset("nino"))
      else combine(set("nino", previousBalance.nino.nino), unset("hashNino"))

    collection
      .updateOne(
        filter = or(equal("hashNino", hashNino), equal("nino", previousBalance.nino.nino)),
        update = combine(
          identifierUpdate,
          set("previousBalance", previousBalance.previousBalance.bigDecimal),
          set("date", previousBalance.date),
          set("expireAt", previousBalance.expireAt),
          set("updateRequired", previousBalance.updateRequired)
        ),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .void
  }

  override def getTestPreviousBalanceRecord(nino: Nino): Future[Option[PreviousBalanceRecord]] =
    collection.find(identifierFilter(nino, includeLegacy = true)).headOption()

  override def getAllTestPreviousBalanceRecords(): Future[Seq[PreviousBalanceRecord]] =
    collection.find().sort(descending("_id")).toFuture()

  override def deleteTestPreviousBalance(nino: Nino): Future[Boolean] =
    collection
      .deleteMany(identifierFilter(nino, includeLegacy = true))
      .toFuture()
      .map(_.getDeletedCount > 0)

  private def getPreviousBalanceRecord(
    nino: Nino,
    additionalFilter: Option[Bson] = None
  ): Future[Option[PreviousBalanceRecord]] = {
    def withAdditionalFilter(filter: Bson): Bson = additionalFilter.fold(filter)(and(filter, _))

    if (config.encryptionEnabled)
      collection
        .find(withAdditionalFilter(equal("hashNino", ninoHash(nino))))
        .headOption()
        .flatMap {
          case found @ Some(_) => Future.successful(found)
          case None =>
            collection
              .find(withAdditionalFilter(equal("nino", nino.nino)))
              .headOption()
              .flatMap {
                case Some(legacyRecord) =>
                  migrateLegacyRecord(nino, additionalFilter)
                    .map(_ => Some(legacyRecord.copy(nino = None, hashNino = Some(ninoHash(nino)))))
                case None => Future.successful(None)
              }
        }
    else collection.find(withAdditionalFilter(equal("nino", nino.nino))).headOption()
  }

  private def migrateLegacyRecord(nino: Nino, additionalFilter: Option[Bson]): Future[Unit] = {
    val filter = additionalFilter.fold[Bson](equal("nino", nino.nino))(and(equal("nino", nino.nino), _))

    collection
      .updateOne(filter, combine(set("hashNino", ninoHash(nino)), unset("nino")))
      .toFuture()
      .void
  }

  private def setHashedPreviousBalance(previousBalance: PreviousBalance): Future[Unit] =
    collection
      .updateOne(
        filter = identifierFilter(previousBalance.nino, includeLegacy = true),
        update = combine(
          set("hashNino", ninoHash(previousBalance.nino)),
          unset("nino"),
          set("previousBalance", previousBalance.previousBalance.bigDecimal),
          set("date", previousBalance.date),
          set("updateRequired", false),
          setOnInsert("expireAt", previousBalance.expireAt)
        ),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()
      .void

  private def identifierFilter(nino: Nino, includeLegacy: Boolean = config.encryptionEnabled): Bson =
    if (includeLegacy) or(equal("hashNino", ninoHash(nino)), equal("nino", nino.nino))
    else equal("nino", nino.nino)
}

case class PreviousBalance(
  nino: Nino,
  previousBalance: BigDecimal,
  date: Instant,
  expireAt: Instant = LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC),
  updateRequired: Boolean = false
)

case class PreviousBalanceRecord(
  nino: Option[Nino],
  hashNino: Option[String],
  previousBalance: BigDecimal,
  date: Instant,
  expireAt: Instant,
  updateRequired: Boolean = false
) {
  def toDomain(requestNino: Nino): PreviousBalance =
    PreviousBalance(requestNino, previousBalance, date, expireAt, updateRequired)
}

object PreviousBalanceRecord {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val formats: OFormat[PreviousBalanceRecord] = Json.format

  def legacy(previousBalance: PreviousBalance): PreviousBalanceRecord =
    PreviousBalanceRecord(
      Some(previousBalance.nino),
      None,
      previousBalance.previousBalance,
      previousBalance.date,
      previousBalance.expireAt,
      previousBalance.updateRequired
    )
}
