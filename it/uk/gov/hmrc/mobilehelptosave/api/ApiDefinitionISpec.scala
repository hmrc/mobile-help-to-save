//
//package uk.gov.hmrc.mobilehelptosave.api
//
//import org.scalatest.concurrent.Eventually
//import play.api.Application
//import uk.gov.hmrc.mobilehelptosave.support.{ApplicationBuilder, BaseISpec, ComponentSupport}
//
//class ApiDefinitionISpec extends BaseISpec with Eventually with ComponentSupport {
//
//  override protected def appBuilder: ApplicationBuilder = super.appBuilder.configure(
//    "api.access.type" -> "TEST_ACCESS_TYPE"
//  )
//
//  override implicit lazy val app: Application = appBuilder.build()
//
//  "GET /api/definition" should {
//
//    "provide definition with configurable whitelist" in {
//      val response = await(wsUrl("/api/definition").get())
//      response.status shouldBe 200
//      println(response.body)
//      response.header("Content-Type") shouldBe Some("application/json")
//      println(response)
//      val definition = response.json
//      (definition \\ "version").map(_.as[String]).head shouldBe "1.0"
//      println(definition)
//      val accessConfigs = definition \ "api" \ "versions" \\ "access"
//      accessConfigs.length should be > 0
//      accessConfigs.foreach { accessConfig =>
//        (accessConfig \ "type").as[String] shouldBe "TEST_ACCESS_TYPE"
//      }
//    }
//  }
//
//  "GET /api/conf/1.0/application.yaml" should {
//    "return RAML" in {
//      val response = await(wsUrl("/api/conf/1.0/application.yaml").get())
//      response.status shouldBe 200
//    }
//  }
//}
