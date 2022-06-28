/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.{LocalDate, LocalDateTime}
import cats.instances.future._
import cats.syntax.functor._
import com.mongodb.client.model.Indexes.text
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.Indexes.descending
import org.mongodb.scala.model.Updates.{combine, set}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.{ErrorInfo, SavingsGoal}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait SavingsGoalEventRepo[F[_]] {

  def setGoal(
    nino:                        Nino,
    amount:                      Option[Double],
    name:                        Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): F[Unit]

  def setTestGoal(
    nino:   Nino,
    amount: Option[Double],
    name:   Option[String],
    date:   LocalDate
  ): F[Unit]

  def deleteGoal(
    nino:                        Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): F[Unit]
  def getGoal(nino:   Nino): F[Option[SavingsGoal]]
  def getEvents(nino: Nino): F[Seq[SavingsGoalEvent]]
  def clearGoalEvents(): F[Boolean]

  def getGoalSetEvents: F[Seq[SavingsGoalSetEvent]]
  def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]]

  def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): F[Unit]
}

class MongoSavingsGoalEventRepo(
  mongo:        MongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[SavingsGoalEvent])
    extends PlayMongoRepository[SavingsGoalEvent](
      collectionName = "savingsGoalEvents",
      mongoComponent = mongo,
      domainFormat   = mongoFormats,
      extraCodecs =
        Codecs.playFormatCodecsBuilder(mongoFormats).forType[SavingsGoalSetEvent].forType[SavingsGoalDeleteEvent].build,
      indexes = Seq(
        IndexModel(descending("expireAt"),
                   IndexOptions()
                     .name("expireAtIdx")
                     .expireAfter(0, TimeUnit.SECONDS)),
        IndexModel(
          text("nino"),
          IndexOptions().name("ninoIdx").unique(false).sparse(true)
        )
      ),
      replaceIndexes = true
    )
    with SavingsGoalEventRepo[Future] {

  override def setGoal(
    nino:                        Nino,
    amount:                      Option[Double],
    name:                        Option[String],
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalSetEvent(nino     = nino,
                            amount   = amount,
                            name     = name,
                            date     = LocalDateTime.now,
                            expireAt = secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay())
      )
      .toFuture()
      .void

  override def setTestGoal(
    nino:   Nino,
    amount: Option[Double],
    name:   Option[String],
    date:   LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalSetEvent(nino     = nino,
                            amount   = amount,
                            name     = name,
                            date     = date.atStartOfDay(),
                            expireAt = date.plusMonths(1).atStartOfDay())
      )
      .toFuture()
      .void

  override def deleteGoal(
    nino:                        Nino,
    secondPeriodBonusPaidByDate: LocalDate
  ): Future[Unit] =
    collection
      .insertOne(
        SavingsGoalDeleteEvent(nino, LocalDateTime.now, secondPeriodBonusPaidByDate.plusMonths(6).atStartOfDay())
      )
      .toFuture()
      .void

  override def clearGoalEvents(): Future[Boolean] =
    collection
      .deleteMany(filter = Document())
      .map(_ => true)
      .recover {
        case _ => false
      }
      .head()

  override def getEvents(nino: Nino): Future[Seq[SavingsGoalEvent]] =
    collection.find(equal("nino", Codecs.toBson(nino))).toFuture()

  override def getGoal(nino: Nino): Future[Option[SavingsGoal]] = {
    val result =
      collection.find(equal("nino", Codecs.toBson(nino))).sort(descending("date")).headOption()
    result.map {
      case None => None
      case Some(_: SavingsGoalDeleteEvent) => None
      case Some(SavingsGoalSetEvent(_, amount, _, name, _, _)) =>
        Some(SavingsGoal(goalName = name, goalAmount = amount))
    }
  }

  override def getGoalSetEvents(): Future[Seq[SavingsGoalSetEvent]] =
    collection
      .find(equal("type", "set"))
      .map {
        case event: SavingsGoalSetEvent => event
        case _ => throw new IllegalStateException("Event must be a set event")
      }
      .toFuture()

  override def getGoalSetEvents(nino: Nino): Future[Either[ErrorInfo, Seq[SavingsGoalSetEvent]]] =
    collection
      .find(Filters.and(equal("type", "set"), equal("nino", Codecs.toBson(nino))))
      .map {
        case event: SavingsGoalSetEvent => event
        case _ => throw new IllegalStateException("Event must be a set event")
      }
      .toFuture()
      .map(Right(_))

  override def updateExpireAt(
    nino:     Nino,
    expireAt: LocalDateTime
  ): Future[Unit] =
    collection
      .updateMany(
        filter = and(equal("nino", Codecs.toBson(nino)), equal("updateRequired", true)),
        update = combine(set("updateRequired", false), set("expireAt", expireAt.toString))
      )
      .toFutureOption()
      .void

}
