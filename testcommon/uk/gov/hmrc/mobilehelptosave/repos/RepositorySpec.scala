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

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.core.errors.{DatabaseException, GenericDatabaseException}

import scala.concurrent.ExecutionContext.Implicits.global

trait RepositorySpec[A <: Any, ID <: Any] extends WordSpecLike with Matchers with FutureAwaits with DefaultAwaitTimeout with Eventually with BeforeAndAfterAll {

  val repo: TestableRepository[A, ID]

  def createId(): ID

  /** Create an ID that is not equal to the one returned by createId() */
  def createOtherId(): ID

  def createEntity(id: ID): A

  /** Create an A that is not equal to the one returned by createEntity() */
  def createOtherEntity(otherId: ID): A

  "insert" should {
    "save data so that it can be found with findById" in {
      val id = createId()
      val otherId = createOtherId()
      try {
        val entity = createEntity(id)
        val otherEntity = createOtherEntity(otherId)

        await(repo.insert(otherEntity)).n shouldBe 1
        await(repo.insert(entity)).n shouldBe 1

        eventually {
          await(repo.findById(id)) shouldBe Some(entity)
        }
      }
      finally {
        await(repo.removeById(id))
        await(repo.removeById(otherId))
      }
    }

    "not allow 2 entities with the same ID to be inserted" in {
      val id = createId()
      try {
        val entity = createEntity(id)
        val entity2 = createOtherEntity(id)

        await(repo.insert(entity))
        eventually {
          await(repo.findById(id)) shouldBe Some(entity)
        }

        val e = intercept[DatabaseException] {
          await(repo.insert(entity2))
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

  "removeById" should {
    "return the removed count" in {
      val id = createId()
      await(repo.removeById(id)).n shouldBe 0

      await(repo.insert(createEntity(id)))
      await(repo.removeById(id)).n shouldBe 1
    }
  }

  override def beforeAll() {
    super.beforeAll()
    await(repo.ensureIndexes)
  }
}
