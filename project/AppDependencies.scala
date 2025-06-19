import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {

  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest
  )

  private val hmrcMongoVersion     = "2.6.0"
  private val bootstrapVersion     = "9.13.0"
  private val domainVersion        = "12.1.0"
  private val catsCoreVersion      = "2.13.0"
  private val scalaUriVersion      = "4.0.3"
  private val playHmrcVersion      = "8.2.0"
  private val enumeratumVersion    = "1.9.0"
  private val macrosVersion        = "2.6.6"
  private val refinedVersion       = "0.11.3"
  private val htsKalcVersion       = "0.8.1"
  private val jacksonModuleVersion = "2.19.1"

  private val scalaMockVersion = "7.3.2"

  private val flexmarkVersion            = "0.64.8"
  private val playJsonVersion = "2.10.6"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc"                  %% "domain-play-30"             % domainVersion,
    "org.typelevel"                %% "cats-core"                  % catsCoreVersion,
    "io.lemonlabs"                 %% "scala-uri"                  % scalaUriVersion,
    "uk.gov.hmrc"                  %% "play-hmrc-api-play-30"      % playHmrcVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    "com.beachape"                 %% "enumeratum-play-json"       % enumeratumVersion,
    "com.softwaremill.macwire"     %% "macros"                     % macrosVersion,
    "eu.timepit"                   %% "refined"                    % refinedVersion,
    "uk.gov.hmrc"                  % "help-to-save-kalculator-jvm" % htsKalcVersion,
    "com.typesafe.play"            %% "play-json"                  % playJsonVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"       % jacksonModuleVersion
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
      "org.scalamock" %% "scalamock" % scalaMockVersion % "test",
      "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion      % "test"
    )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq.empty

  def testCommon(scope: String): Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % scope,
    "com.vladsch.flexmark" % "flexmark-all"                 % flexmarkVersion   % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.4.57.v20241219" % scope
  )
}
