/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.mobilehelptosave.repos

import org.joda.time.DateTime
import org.joda.time.chrono.ISOChronology
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}

class InvitationMongoFormatTest extends WordSpec with Matchers {

  "mongoFormat" should {
    "rename _id to internalAuthId when reading whilst leaving other fields unchanged" in {
      val json = Json.obj(
        "_id" -> "test-internal-auth-id",
        "created" -> 1086776430000L
      )

      val parsedInvitation = json.as[Invitation](InvitationMongoFormat.mongoFormat)

      // toDateTime(ISOChronology.getInstance()) is needed to match what
      // play.api.libs.json.DefaultReads.DefaultJodaDateReads does
      parsedInvitation shouldBe Invitation(
        InternalAuthId("test-internal-auth-id"),
        DateTime.parse("2004-06-09T10:20:30Z").toDateTime(ISOChronology.getInstance())
      )
    }

    "rename internalAuthId to _id when writing whilst leaving other fields unchanged" in {
      val invitation = Invitation(
        InternalAuthId("test-internal-auth-id"),
        DateTime.parse("2004-06-09T10:20:30Z")
      )

      Json.toJson(invitation)(InvitationMongoFormat.mongoFormat) shouldBe Json.obj(
        "_id" -> "test-internal-auth-id",
        "created" -> 1086776430000L
      )
    }
  }

}
