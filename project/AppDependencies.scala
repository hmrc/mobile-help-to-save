import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt.{ModuleID, _}

object AppDependencies {

  lazy val appDependencies: Seq[ModuleID] =
    compile ++ test ++ integrationTest

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.5.0",
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0",
    "org.typelevel" %% "cats-core" % "1.0.1",
    "io.lemonlabs" %% "scala-uri" % "1.1.1",
    "ai.x" %% "play-json-extensions" % "0.9.0"
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % "4.0.0" % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
    "com.github.tomakehurst" % "wiremock" % "2.13.0" % "it"
  )

  def testCommon(scope: String) = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.4" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
  )

}
