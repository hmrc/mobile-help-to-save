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

import java.time.LocalDateTime

import cats.instances.future._
import cats.syntax.functor._
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, Future}

trait PreviousBalanceRepo[F[_]] {

  def setPreviousBalance(
    nino:            Nino,
    previousBalance: BigDecimal
  ): F[Unit]

  def getPreviousBalance(nino: Nino): F[Option[PreviousBalance]]

  def clearPreviousBalance(): Future[Unit]
}

class MongoPreviousBalanceRepo(
  mongo:        ReactiveMongoComponent
)(implicit ec:  ExecutionContext,
  mongoFormats: Format[PreviousBalance])
    extends IndexedMongoRepo[Nino, PreviousBalance]("previousBalance", "nino", unique = false, mongo = mongo)
    with PreviousBalanceRepo[Future] {

  override def setPreviousBalance(
    nino:            Nino,
    previousBalance: BigDecimal
  ): Future[Unit] =
    findAndUpdate(query  = obj(indexFieldName -> Json.toJson(nino)),
                  update = obj("$set" -> Json.toJson(PreviousBalance(nino, previousBalance, LocalDateTime.now()))),
                  upsert = true).void

  override def getPreviousBalance(nino: Nino): Future[Option[PreviousBalance]] =
    collection.find(obj("nino" -> nino), None)(JsObjectDocumentWriter, JsObjectDocumentWriter).one[PreviousBalance]

  override def clearPreviousBalance(): Future[Unit] =
    removeAll().void
}

case class PreviousBalance(
  nino:            Nino,
  previousBalance: BigDecimal,
  date:            LocalDateTime)

object PreviousBalance {
  implicit val formats: OFormat[PreviousBalance] = Json.format
}
