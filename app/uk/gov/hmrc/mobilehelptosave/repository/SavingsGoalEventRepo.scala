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

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import cats.instances.future.*
import cats.syntax.functor.*
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.*
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait SavingsGoalEventRepo {

  def setGoal(
    nino: Nino,
    amount: Option[Double],
    name: Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit]

  def setTestGoal(
    nino: Nino,
    amount: Option[Double],
    name: Option[String],
    date: LocalDate
  ): Future[Unit]

  def deleteGoal(
    nino: Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit]
  def getGoal(nino: Nino): Future[Option[SavingsGoal]]
  def getEvents(nino: Nino): Future[Seq[SavingsGoalEvent]]
  def clearGoalEvents(): Future[Boolean]

  def getGoalSetEvents: Future[Seq[SavingsGoalSetEvent]]
  def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]]
  def updateExpireAt(): Future[Unit]

  def updateExpireAt(
    nino: Nino,
    expireAt: LocalDateTime
  ): Future[Unit]

}

class MongoSavingsGoalEventRepo(
  mongo: MongoComponent
)(implicit ec: ExecutionContext, mongoFormats: Format[SavingsGoalEvent])
    extends PlayMongoRepository[SavingsGoalEvent](
      collectionName = "savingsGoalEvents",
      mongoComponent = mongo,
      domainFormat   = mongoFormats,
      extraCodecs    = Codecs.playFormatCodecsBuilder(mongoFormats).forType[SavingsGoalSetEvent].forType[SavingsGoalDeleteEvent].build,
      indexes = Seq(
        IndexModel(descending("expireAt"),
                   IndexOptions()
                     .name("expireAtIdx")
                     .expireAfter(0, TimeUnit.SECONDS)
                  ),
        IndexModel(
          ascending("nino"),
          IndexOptions().name("ninoIdx").unique(false).sparse(true)
        )
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
    collection
      .insertOne(
        SavingsGoalSetEvent(
          nino     = nino,
          amount   = amount,
          name     = name,
          date     = Instant.now,
          expireAt = secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC)
        )
      )
      .toFuture()
      .void

  override def setTestGoal(
    nino: Nino,
    amount: Option[Double],
    name: Option[String],
    date: LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalSetEvent(
          nino     = nino,
          amount   = amount,
          name     = name,
          date     = date.atStartOfDay().toInstant(ZoneOffset.UTC),
          expireAt = date.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        )
      )
      .toFuture()
      .void

  override def deleteGoal(
    nino: Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalDeleteEvent(
          nino,
          Instant.now,
          secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC)
        )
      )
      .toFuture()
      .void

  override def clearGoalEvents(): Future[Boolean] =
    collection
      .deleteMany(filter = Document())
      .map(_ => true)
      .recover { case _ =>
        false
      }
      .head()

  override def getEvents(nino: Nino): Future[Seq[SavingsGoalEvent]] =
    collection.find(equal("nino", nino.nino)).toFuture()

  override def getGoal(nino: Nino): Future[Option[SavingsGoal]] = {
    val result =
      collection.find(equal("nino", nino.nino)).sort(descending("date")).headOption()
    result.map {
      case None                            => None
      case Some(_: SavingsGoalDeleteEvent) => None
      case Some(SavingsGoalSetEvent(_, amount, _, name, _, _)) =>
        Some(SavingsGoal(goalName = name, goalAmount = amount))
    }
  }

  override def getGoalSetEvents: Future[Seq[SavingsGoalSetEvent]] =
    collection
      .find(equal("type", "set"))
      .map {
        case event: SavingsGoalSetEvent => event
        case _                          => throw new IllegalStateException("Event must be a set event")
      }
      .toFuture()

  override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]] =
    collection
      .find(Filters.and(equal("type", "set"), equal("nino", nino.nino)))
      .map {
        case event: SavingsGoalSetEvent => event
        case _                          => throw new IllegalStateException("Event must be a set event")
      }
      .toFuture()
      .map(Right(_))

  override def updateExpireAt(): Future[Unit] =
    collection
      .updateMany(
        filter = Document(),
        update = combine(set("updateRequired", true),
                         set("expireAt", LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC)),
                         set("date", Instant.now())
                        )
      )
      .toFutureOption()
      .void

  override def updateExpireAt(
    nino: Nino,
    expireAt: LocalDateTime
  ): Future[Unit] =
    collection
      .updateMany(
        filter = and(equal("nino", Codecs.toBson(nino)), equal("updateRequired", true)),
        update = combine(set("updateRequired", false), set("expireAt", expireAt.toInstant(ZoneOffset.UTC)))
      )
      .toFutureOption()
      .void

}
