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

import java.time.{Instant, LocalDateTime, ZoneOffset}
import cats.instances.future.*
import cats.syntax.functor.*
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.{FindOneAndReplaceOptions, IndexModel, IndexOptions}
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.Updates.{combine, set}
import play.api.libs.json.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait PreviousBalanceRepo {

  def setPreviousBalance(
    nino: Nino,
    previousBalance: BigDecimal,
    finalBonusPaidByDate: LocalDateTime
  ): Future[Unit]

  def getPreviousBalance(nino: Nino): Future[Option[PreviousBalance]]

  def clearPreviousBalance(): Future[Unit]

  def updateExpireAt(): Future[Unit]

  def updateExpireAt(
    nino: Nino,
    expireAt: LocalDateTime
  ): Future[Unit]

  def getPreviousBalanceUpdateRequired(nino: Nino): Future[Option[PreviousBalance]]

}

class MongoPreviousBalanceRepo(
  mongo: MongoComponent
)(implicit ec: ExecutionContext, mongoFormats: Format[PreviousBalance])
    extends PlayMongoRepository[PreviousBalance](
      collectionName = "previousBalance",
      mongoComponent = mongo,
      domainFormat   = mongoFormats,
      indexes = Seq(
        IndexModel(descending("expireAt"),
                   IndexOptions()
                     .name("expireAtIdx")
                     .expireAfter(0, TimeUnit.SECONDS)
                  ),
        IndexModel(ascending("nino"), IndexOptions().name("ninoIdx").unique(false).sparse(true))
      ),
      replaceIndexes = true
    )
    with PreviousBalanceRepo {

  override def setPreviousBalance(
    nino: Nino,
    previousBalance: BigDecimal,
    finalBonusPaidByDate: LocalDateTime
  ): Future[Unit] =
    collection
      .findOneAndReplace(
        filter      = equal("nino", nino.nino),
        replacement = PreviousBalance(nino, previousBalance, Instant.now, finalBonusPaidByDate.plusMonths(6) toInstant (ZoneOffset.UTC)),
        options     = FindOneAndReplaceOptions().upsert(true)
      )
      .toFuture()
      .void

  override def getPreviousBalance(nino: Nino): Future[Option[PreviousBalance]] =
    collection.find(equal("nino", nino.nino)).headOption()

  override def clearPreviousBalance(): Future[Unit] =
    collection.deleteMany(filter = Document()).toFuture().void

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
        update = combine(set("updateRequired", false), set("expireAt", expireAt))
      )
      .toFutureOption()
      .void

  override def getPreviousBalanceUpdateRequired(nino: Nino): Future[Option[PreviousBalance]] =
    collection.find(and(equal("nino", Codecs.toBson(nino)), equal("updateRequired", true))).headOption()
}

case class PreviousBalance(nino: Nino,
                           previousBalance: BigDecimal,
                           date: Instant,
                           expireAt: Instant = LocalDateTime.now(ZoneOffset.UTC).plusMonths(54).toInstant(ZoneOffset.UTC),
                           updateRequired: Boolean = false
                          )

object PreviousBalance {
  implicit val dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit val formats: OFormat[PreviousBalance] = Json.format
}
