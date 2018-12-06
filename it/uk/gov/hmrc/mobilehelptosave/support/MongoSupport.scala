package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Random, Try}

/**
  * Fold this into your test class and you will get:
  *   - a mongo database with a name of the form "mobile-help-to-save-xxxxxx" where "xxxxxx" is a random number
  *   - the database will be dropped after each test, so tests know that they are starting with a clean db
  */
trait MongoSupport extends BeforeAndAfterEach with AppBuilder {
  self: Suite with ServerProvider =>

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder.configure(
    "mongodb.uri" -> s"mongodb://localhost:27017/mobile-help-to-save-${Random.nextInt().abs}"
  )

  override protected def afterEach(): Unit = {
    val mongo: ReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
    val db: DefaultDB = mongo.mongoConnector.db()
    Try(Await.ready(db.drop(), 10 seconds))

    super.afterEach()
  }
}
