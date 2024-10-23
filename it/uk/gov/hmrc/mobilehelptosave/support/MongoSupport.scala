/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import org.scalatestplus.play.components.WithApplicationComponents

import scala.util.{Random, Try}

/**
  * Fold this into your test class and you will get:
  *   - a mongo database with a name of the form "mobile-help-to-save-xxxxxx" where "xxxxxx" is a random number
  *   - the database will be dropped after each test, so tests know that they are starting with a clean db
  */
trait MongoSupport extends BeforeAndAfterEach with AppBuilder with ComponentSupport with WithApplicationComponents {
  self: Suite with ServerProvider =>

  override protected def appBuilder: ApplicationBuilder = super.appBuilder.configure(
    "mongodb.uri" -> s"mongodb://localhost:27017/mobile-help-to-save-${Random.nextInt().abs}"
  )

  override protected def afterEach(): Unit = {
    val db = components.mongo.database
    Try(db.drop())

    super.afterEach()
  }
}
