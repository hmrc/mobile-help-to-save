import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {

  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest,
    resolvers += "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
  )

  // This is the highest version of play-reactivemongo that does not have performance problems
  // when mongo servers are unavailable or restart - don't bump until those issues are resolved!
  private val reactiveMongoVersion = "6.2.0"

  // This is the highest version of enumeratum-play-json that supports play 2.5
  private val enumeratumVersion = "1.5.11"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-25" % "4.3.0" withSources(),
    "uk.gov.hmrc" %% "domain" % "5.2.0",
    "org.typelevel" %% "cats-core" % "1.5.0",
    "io.lemonlabs" %% "scala-uri" % "1.4.0",
    "uk.gov.hmrc" %% "play-hmrc-api" % "3.3.0-play-25",
    "uk.gov.hmrc" %% "play-reactivemongo" % reactiveMongoVersion,
    "com.beachape" %% "enumeratum-play-json" % enumeratumVersion,
    "com.softwaremill.macwire" %% "macros" % "2.3.1"
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % "4.1.0" % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq (
    "com.github.tomakehurst" % "wiremock" % "2.19.0" % "it"
  )

  def testCommon(scope: String): Seq[ModuleID] = Seq(
    "org.scalacheck" %% "scalacheck" % "1.14.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "com.eclipsesource" %% "play-json-schema-validator" % "0.8.9" % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.2.22.v20170606" % scope
  )
}
