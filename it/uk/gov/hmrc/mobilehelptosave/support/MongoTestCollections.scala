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

package uk.gov.hmrc.mobilehelptosave.support

import java.util.UUID

import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Use differently-named collections to avoid interfering with non-test data.
  *
  * To use:
  * - mix in this trait into Specs
  * - in Specs:
  *   - build the app from appBuilder
  *     and
  *   - call dropTestCollections() just before stopping the test Play Application (or use MongoTestCollectionsDropAfterAll)
  * - in repositories, suffix the collection name with the value of the `mongodb.collectionName.suffix` app config property
  */
trait MongoTestCollections extends AppBuilder {
  protected lazy val collectionNameSuffix = s"-test-${UUID.randomUUID()}"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder.configure("mongodb.collectionName.suffix" -> collectionNameSuffix)

  protected def db(app: Application): DefaultDB = app.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db()

  protected def dropTestCollections(db: DefaultDB): Future[Unit] = {
    db.collectionNames.map { collectionNames =>
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
  }
}

trait MongoTestCollectionsDropAfterAll extends MongoTestCollections with BeforeAndAfterAll with FutureAwaits with DefaultAwaitTimeout { this: Suite =>
  protected def app: Application

  override protected def afterAll(): Unit = {
    super.afterAll()

    await(dropTestCollections(db(app)))
  }
}
