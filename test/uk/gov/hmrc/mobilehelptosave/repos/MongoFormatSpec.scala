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
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation, NinoWithoutWtc}

class MongoFormatSpec extends WordSpec with Matchers {

  private val generator = new Generator(0)
  private val nino = generator.nextNino

  "NinoWithoutWtcMongoRepository.domainFormat" should {
    // this behaviour depends on an import of ReactiveMongoFormats._, which IntelliJ will remove if you organise imports
    "write date as a MongoDB Date, not an integer, so that a TTL index can be used" in {
      val created = new DateTime("1980-01-01T00:00:00Z")
      val json = NinoWithoutWtcMongoRepository.domainFormat.writes(NinoWithoutWtc(nino, created))
      (json \ "created" \ "$date").toOption.isDefined shouldBe true
    }
  }

  "InvitationMongoRepository.domainFormat" should {
    "write date as a MongoDB Int64 for compatibility with existing data" in {
      val created = new DateTime("1980-01-01T00:00:00Z")

      val json = InvitationMongoRepository.domainFormat.writes(Invitation(InternalAuthId("testId"), created))
      (json \ "created").toOption.isDefined shouldBe true
      (json \ "created" \ "$date").toOption.isDefined shouldBe false
    }
  }
}
