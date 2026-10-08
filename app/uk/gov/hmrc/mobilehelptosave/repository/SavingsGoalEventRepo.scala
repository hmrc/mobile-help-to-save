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
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.config.MongoConfig
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait SavingsGoalEventRepo {
  def setGoal(nino: Nino, amount: Option[Double], name: Option[String], secondPeriodBonusPaidByDate: LocalDate): Future[Unit]

  def setTestGoal(nino: Nino, amount: Option[Double], name: Option[String], date: LocalDate): Future[Unit]

  def deleteGoal(nino: Nino, secondPeriodBonusPaidByDate: LocalDate): Future[Unit]
  def getGoal(nino: Nino): Future[Option[SavingsGoal]]
  def getEvents(nino: Nino): Future[Seq[SavingsGoalEvent]]
  def clearGoalEvents(): Future[Boolean]

  def getGoalSetEvents: Future[Seq[SavingsGoalSetEvent]]
  def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]]
  def updateExpireAt(): Future[Unit]
  def updateExpireAt(nino: Nino, expireAt: LocalDateTime): Future[Unit]

  def setTestGoalEvent(event: SavingsGoalEvent, isHashed: Boolean): Future[Unit]
  def getTestGoalEventRecords(nino: Nino): Future[Seq[SavingsGoalEventRecord]]
  def getAllTestGoalEventRecords(): Future[Seq[SavingsGoalEventRecord]]
  def deleteTestGoalEvents(nino: Nino): Future[Boolean]
}

class MongoSavingsGoalEventRepo(
  mongo: MongoComponent,
  config: MongoConfig,
  ninoHash: NinoHash
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SavingsGoalEventRecord](
      collectionName = "savingsGoalEvents",
      mongoComponent = mongo,
      domainFormat   = SavingsGoalEventRecord.format,
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
    with SavingsGoalEventRepo {

  override def setGoal(
    nino: Nino,
    amount: Option[Double],
    name: Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    insertEvent(
      SavingsGoalSetEvent(
        nino     = nino,
        amount   = amount,
        name     = name,
        date     = Instant.now(),
        expireAt = configuredExpiry(secondPeriodBonusPaidByDate)
      )
    )

  override def setTestGoal(
    nino: Nino,
    amount: Option[Double],
    name: Option[String],
    date: LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalEventRecord.fromDomain(
          SavingsGoalSetEvent(
            nino     = nino,
            amount   = amount,
            name     = name,
            date     = date.atStartOfDay().toInstant(ZoneOffset.UTC),
            expireAt = date.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
          ),
          hashNino = None
        )
      )
      .toFuture()
      .void

  override def deleteGoal(nino: Nino, secondPeriodBonusPaidByDate: LocalDate): Future[Unit] =
    insertEvent(
      SavingsGoalDeleteEvent(
        nino,
        Instant.now(),
        configuredExpiry(secondPeriodBonusPaidByDate)
      )
    )

  override def clearGoalEvents(): Future[Boolean] =
    collection
      .deleteMany(filter = Document())
      .map(_ => true)
      .recover { case _ => false }
      .head()

  override def getEvents(nino: Nino): Future[Seq[SavingsGoalEvent]] =
    findRecords(nino).map(_.map(_.toDomain(nino)))

  override def getGoal(nino: Nino): Future[Option[SavingsGoal]] =
    findRecords(nino).map(_.sortBy(_.date).lastOption.flatMap {
      case _: SavingsGoalDeleteEventRecord                        => None
      case SavingsGoalSetEventRecord(_, _, amount, _, name, _, _) => Some(SavingsGoal(goalName = name, goalAmount = amount))
    })

  override def getGoalSetEvents: Future[Seq[SavingsGoalSetEvent]] =
    collection
      .find(equal("type", "set"))
      .toFuture()
      .map(_.map {
        case record: SavingsGoalSetEventRecord if record.nino.isDefined => record.toDomain(record.nino.get)
        case _: SavingsGoalSetEventRecord =>
          throw new IllegalStateException("A NINO is required when retrieving all savings goal set events")
        case _ => throw new IllegalStateException("Event must be a set event")
      })

  override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]] =
    findRecords(nino, additionalFilter = Some(equal("type", "set")))
      .map(_.map {
        case record: SavingsGoalSetEventRecord => record.toDomain(nino)
        case _                                 => throw new IllegalStateException("Event must be a set event")
      })
      .map(Right(_))

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
      else
        combine(
          set("updateRequired", false),
          set("expireAt", expireAt.toInstant(ZoneOffset.UTC))
        )

    collection
      .updateMany(
        filter = and(identifierFilter(nino), equal("updateRequired", true)),
        update = update
      )
      .toFutureOption()
      .void
  }

  override def setTestGoalEvent(event: SavingsGoalEvent, isHashed: Boolean): Future[Unit] =
    collection
      .insertOne(SavingsGoalEventRecord.fromDomain(event, Option.when(isHashed)(ninoHash(event.nino))))
      .toFuture()
      .void

  override def getTestGoalEventRecords(nino: Nino): Future[Seq[SavingsGoalEventRecord]] =
    collection.find(identifierFilter(nino, encryptionEnabled = true)).sort(descending("date")).toFuture()

  override def getAllTestGoalEventRecords(): Future[Seq[SavingsGoalEventRecord]] =
    collection.find().sort(descending("_id")).toFuture()

  override def deleteTestGoalEvents(nino: Nino): Future[Boolean] =
    collection
      .deleteMany(identifierFilter(nino, encryptionEnabled = true))
      .toFuture()
      .map(_.getDeletedCount > 0)

  private def insertEvent(event: SavingsGoalEvent): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalEventRecord.fromDomain(
          event,
          Option.when(config.encryptionEnabled)(ninoHash(event.nino))
        )
      )
      .toFuture()
      .void

  private def findRecords(
    nino: Nino,
    additionalFilter: Option[Bson] = None
  ): Future[Seq[SavingsGoalEventRecord]] = {
    if (config.encryptionEnabled)
      for {
        hashed <- collection
                    .find(withAdditionalFilter(equal("hashNino", ninoHash(nino)), additionalFilter))
                    .toFuture()
        legacy <- collection
                    .find(withAdditionalFilter(equal("nino", nino.nino), additionalFilter))
                    .toFuture()
        _      <- migrateLegacyRecords(nino, legacy.nonEmpty, additionalFilter)
      } yield (hashed ++ legacy.map(withHashedIdentifier(_, nino))).sortBy(_.date)
    else
      collection
        .find(withAdditionalFilter(equal("nino", nino.nino), additionalFilter))
        .sort(ascending("date"))
        .toFuture()
  }

  private def migrateLegacyRecords(nino: Nino, recordsFound: Boolean, additionalFilter: Option[Bson]): Future[Unit] =
    if (recordsFound)
      collection
        .updateMany(
          withAdditionalFilter(equal("nino", nino.nino), additionalFilter),
          combine(set("hashNino", ninoHash(nino)), unset("nino"))
        )
        .toFuture()
        .void
    else Future.unit

  private def withHashedIdentifier(record: SavingsGoalEventRecord, nino: Nino): SavingsGoalEventRecord = record match {
    case event: SavingsGoalSetEventRecord    => event.copy(nino = None, hashNino = Some(ninoHash(nino)))
    case event: SavingsGoalDeleteEventRecord => event.copy(nino = None, hashNino = Some(ninoHash(nino)))
  }

  private def configuredExpiry(secondPeriodBonusPaidByDate: LocalDate): Instant =
    secondPeriodBonusPaidByDate
      .plusMonths(config.savingsGoalEventsTtlMonths)
      .atStartOfDay()
      .toInstant(ZoneOffset.UTC)

  private def withAdditionalFilter(identifier: Bson, additionalFilter: Option[Bson]): Bson =
    additionalFilter.fold(identifier)(and(identifier, _))

  private def identifierFilter(nino: Nino, encryptionEnabled: Boolean = config.encryptionEnabled): Bson =
    if (encryptionEnabled) or(equal("hashNino", ninoHash(nino)), equal("nino", nino.nino))
    else equal("nino", nino.nino)
}
