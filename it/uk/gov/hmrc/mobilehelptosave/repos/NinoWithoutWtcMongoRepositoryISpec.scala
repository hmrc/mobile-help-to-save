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

import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobilehelptosave.domain.NinoWithoutWtc

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NinoWithoutWtcMongoRepositoryISpec extends NinoWithoutWtcRepositorySpec with MongoRepositoryISpec[NinoWithoutWtc, Nino] {
  override val repo: NinoWithoutWtcMongoRepository = app.injector.instanceOf[NinoWithoutWtcMongoRepository]

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder.configure(
    "helpToSave.taxCreditsCache.expireAfterSeconds" -> 1L
  )

  "documents" should {
    "expire" in {
      val id = createId()
      val entity = createEntity(id)

      // https://docs.mongodb.com/v3.2/core/index-ttl/#timing-of-the-delete-operation
      val mongoDeleteExpiredBackgroundTaskRunsEvery = 60 seconds
      implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = mongoDeleteExpiredBackgroundTaskRunsEvery + (10 seconds))

      await(repo.insert(entity))
      eventually {
        await(repo.findById(id)).isDefined shouldBe true
      }

      eventually {
        await(repo.findById(id)) shouldBe None
      }
    }
  }
}
