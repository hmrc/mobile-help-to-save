/*
 * Copyright 2019 HM Revenue & Customs
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

import cats.instances.future._
import cats.syntax.functor._
import play.api.libs.json.Json.obj
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.Message

import scala.concurrent.{ExecutionContext, Future}

trait MessagesRepo[F[_]] {
  def setMessage(message: Message): F[Unit]

  def getMessages(nino: Nino): F[List[Message]]

  def markAsSeen(messageId: String): F[Unit]
}

class MongoMessagesRepo(
  mongo:       ReactiveMongoComponent
)(implicit ec: ExecutionContext, mongoFormats: Format[Message])
    extends IndexedMongoRepo[Nino, Message]("messages", "nino", unique = false, mongo = mongo)
    with MessagesRepo[Future] {

  override def setMessage(message: Message): Future[Unit] =
    findAndUpdate(
      query  = obj(indexFieldName -> Json.toJson(message.nino), "messageType" -> Json.toJson(message.messageType)),
      update = obj("$set" -> Json.toJson(message)),
      upsert = true
    ).void

  override def getMessages(nino: Nino): Future[List[Message]] =
    find("nino" -> Json.toJson(nino))

  override def markAsSeen(messageId: String): Future[Unit] =
    findAndUpdate(
      query  = obj("_id" -> messageId),
      update = obj("$set" -> Json.obj("seen" -> true)),
      upsert = true
    ).void

}