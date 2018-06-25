import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {


  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest,
    resolvers += "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
  )

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.6.0" withSources(),
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0",
    "org.typelevel" %% "cats-core" % "1.0.1",
    "io.lemonlabs" %% "scala-uri" % "1.1.1",
    "uk.gov.hmrc" %% "play-hmrc-api" % "2.1.0"
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % "4.0.0" % "test",
    "com.eclipsesource" %% "play-json-schema-validator" % "0.8.9" % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
    "com.github.tomakehurst" % "wiremock" % "2.13.0" % "it"
  )

  def testCommon(scope: String) = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.4" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.2.22.v20170606" % scope
  )

}
