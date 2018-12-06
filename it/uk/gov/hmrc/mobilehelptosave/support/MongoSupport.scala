package uk.gov.hmrc.mobilehelptosave.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait MongoSupport extends BeforeAndAfterEach with AppBuilder {
  self: Suite with ServerProvider =>

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder.configure(
    "mongodb.uri" -> s"mongodb://localhost:27017/mobile-help-to-save-${Random.nextInt().abs}"
  )
  override protected def afterEach(): Unit = {
    val mongo: ReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
    val db: DefaultDB = mongo.mongoConnector.db()
    db.drop()

    super.afterEach()
  }
}
