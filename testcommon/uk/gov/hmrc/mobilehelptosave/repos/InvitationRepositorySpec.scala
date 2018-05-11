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

import java.util.UUID

import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.mobilehelptosave.domain.{InternalAuthId, Invitation}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * This might look like it is just testing library functionality, but it's
  * also testing our use of that functionality - in particular us using our
  * own ID (internalAuthId) as the Mongo ID instead of using BSONDocumentID
  * which seems to be a much more common usage pattern (based on searching
  * github org:hmrc).
  *
  * It is also used to make sure our test FakeInvitationRepository behaves like
  * the production InvitationMongoRepository
  */
trait InvitationRepositorySpec extends RepositorySpec[Invitation, InternalAuthId] {

  override val repo: InvitationRepository

  def createId(): InternalAuthId = InternalAuthId(s"test-${UUID.randomUUID()}")
  def createOtherId(): InternalAuthId = InternalAuthId(s"test-other-${UUID.randomUUID()}")

  def createEntity(id: InternalAuthId): Invitation = Invitation(id, DateTime.now(DateTimeZone.UTC))
  def createOtherEntity(otherId: InternalAuthId): Invitation = Invitation(otherId, DateTime.now(DateTimeZone.UTC).minusDays(1))

  "countCreatedSince" should {
    "include invitations created at or after the time but not before" in {
      val beforeId = InternalAuthId(s"before-${UUID.randomUUID()}")
      val atId = InternalAuthId(s"at-${UUID.randomUUID()}")
      val afterId = InternalAuthId(s"after-${UUID.randomUUID()}")
      val ids = Seq(beforeId, atId, afterId)

      val at = new DateTime("2017-12-25T10:20:30Z")
      val before = at.minusMillis(1)
      val after = at.plusMillis(1)

      val beforeInvitation = Invitation(beforeId, before)
      val atInvitation = Invitation(atId, at)
      val afterInvitation = Invitation(afterId, after)

      try {
        await(repo.insert(atInvitation))
        await(repo.countCreatedSince(at)) shouldBe 1

        await(repo.insert(beforeInvitation))
        await(repo.countCreatedSince(at)) shouldBe 1

        await(repo.insert(afterInvitation))
        await(repo.countCreatedSince(at)) shouldBe 2
      } finally {
        ids.foreach(id => await(repo.removeById(id)))
      }
    }
  }
}
