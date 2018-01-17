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

import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.core.errors.{DatabaseException, GenericDatabaseException}
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
trait InvitationRepositorySpec extends WordSpec with Matchers with FutureAwaits with DefaultAwaitTimeout with Eventually with BeforeAndAfterAll {

  val repo: InvitationRepository

  "insert" should {
    "save data so that it can be found with findById" in {
      val id = InternalAuthId(s"test-${UUID.randomUUID()}")
      val otherId = InternalAuthId(s"test-other-${UUID.randomUUID()}")
      try {
        val invitation = Invitation(id, DateTime.now())
        val otherInvitation = Invitation(otherId, DateTime.now().minusDays(1))

        await(repo.insert(otherInvitation)).n shouldBe 1
        await(repo.insert(invitation)).n shouldBe 1

        eventually {
          await(repo.findById(id)) shouldBe Some(invitation)
        }
      }
      finally {
        await(repo.removeById(id))
        await(repo.removeById(otherId))
      }
    }

    "not allow 2 invitations with the same ID to be inserted" in {
      val id = InternalAuthId(s"test-${UUID.randomUUID()}")
      try {
        val invitation = Invitation(id, DateTime.now())
        val invitation2 = Invitation(id, DateTime.now().minusDays(1))

        await(repo.insert(invitation))
        eventually {
          await(repo.findById(id)) shouldBe Some(invitation)
        }

        val e = intercept[DatabaseException] {
          await(repo.insert(invitation2))
        }

        repo.isDuplicateKey(e) shouldBe true

        val someOtherE = GenericDatabaseException("something else", Some(1234))
        repo.isDuplicateKey(someOtherE) shouldBe false
      }
      finally {
        await(repo.removeById(id))

        eventually {
          await(repo.findById(id)) shouldBe None
        }
      }
    }
  }

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

  "removeById" should {
    "return the removed count" in {
      val id = InternalAuthId(s"test-${UUID.randomUUID()}")
      await(repo.removeById(id)).n shouldBe 0

      await(repo.insert(Invitation(id, DateTime.now())))
      await(repo.removeById(id)).n shouldBe 1
    }
  }

  override def beforeAll() {
    super.beforeAll()
    await(repo.ensureIndexes)
  }
}
