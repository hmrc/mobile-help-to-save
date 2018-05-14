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

import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MongoRepositoryISpec[A <: Any, ID <: Any] extends RepositorySpec[A, ID] with GuiceOneAppPerSuite with BeforeAndAfterAll {
  override implicit lazy val app: Application = appBuilder
    .build()

  private lazy val collectionNameSuffix = s"-test-${UUID.randomUUID()}"

  protected def appBuilder: GuiceApplicationBuilder = {
    new GuiceApplicationBuilder()
      .configure("mongodb.collectionName.suffix" -> collectionNameSuffix)
  }

  override val repo: ReactiveRepository[A, ID] with TestableRepository[A, ID]

  private def mongo = app.injector.instanceOf[ReactiveMongoComponent]
  private def db: DefaultDB = mongo.mongoConnector.db()

  override protected def afterAll(): Unit = {
    super.afterAll()

    val dropTestCollectionsF = db.collectionNames.map { collectionNames =>
      val dropOFs: Seq[Option[Future[Boolean]]] = collectionNames.map { collectionName =>
        if (collectionName.endsWith(collectionNameSuffix)) {
          val dropF = db.collection[JSONCollection](collectionName).drop(failIfNotFound = true)
          Some(dropF)
        } else {
          None
        }
      }
      val dropFs: Seq[Future[Boolean]] = dropOFs.flatten
      Future.sequence(dropFs)
    }
    await(dropTestCollectionsF)
  }
}
