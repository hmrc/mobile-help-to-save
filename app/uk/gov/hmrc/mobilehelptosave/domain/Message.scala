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

package uk.gov.hmrc.mobilehelptosave.domain
import java.time.LocalDateTime

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino

case class Message(
  _id:         String = BSONObjectID.generate().stringify,
  nino:        Nino,
  messageType: MessageType,
  message:     String,
  seen:        Boolean = false,
  date:        LocalDateTime) {

  def toApiMessage: ApiMessage =
    ApiMessage(
      messageId   = _id,
      messageType = messageType,
      message     = message,
      seen        = seen,
      date        = date
    )
}

object Message {
  implicit val formats: OFormat[Message] = Json.format
}

case class ApiMessage(messageId: String, messageType: MessageType, message: String, seen: Boolean, date: LocalDateTime)

object ApiMessage {
  implicit val formats: OFormat[ApiMessage] = Json.format
}

sealed trait MessageType

case object BalanceReached extends MessageType

object MessageType {
  implicit val messageTypeFormat: Format[MessageType] = new Format[MessageType] {
    override def reads(json: JsValue): JsResult[MessageType] = json.as[String] match {
      case "BalanceReached" => JsSuccess(BalanceReached)
      case _                => JsError("Invalid message type")
    }
    override def writes(messageType: MessageType) = JsString(messageType.toString)
  }
}
