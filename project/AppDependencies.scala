import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {

  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest,
    resolvers += "emueller-bintray" at "https://dl.bintray.com/emueller/maven"
  )

  private val hmrcMongoVersion     = "2.5.0"
  private val bootstrapVersion     = "9.11.0"
  private val domainVersion        = "10.0.0"
  private val catsCoreVersion      = "2.13.0"
  private val catsParVersion       = "1.0.0-RC2"
  private val scalaUriVersion      = "4.0.3"
  private val playHmrcVersion      = "8.0.0"
  private val enumeratumVersion    = "1.8.2"
  private val macrosVersion        = "2.6.6"
  private val refinedVersion       = "0.11.3"
  private val htsKalcVersion       = "0.8.1"
  private val jacksonModuleVersion = "2.14.2"

  private val scalaMockVersion = "5.2.0"

  private val pegdownVersion            = "1.6.0"
  private val playJsonExtensionsVersion = "0.42.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc"                  %% "domain-play-30"             % domainVersion,
    "org.typelevel"                %% "cats-core"                  % catsCoreVersion,
    "io.chrisdavenport"            %% "cats-par"                   % catsParVersion,
    "io.lemonlabs"                 %% "scala-uri"                  % scalaUriVersion,
    "uk.gov.hmrc"                  %% "play-hmrc-api-play-30"      % playHmrcVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    "com.beachape"                 %% "enumeratum-play-json"       % enumeratumVersion,
    "com.softwaremill.macwire"     %% "macros"                     % macrosVersion,
    "eu.timepit"                   %% "refined"                    % refinedVersion,
    "uk.gov.hmrc"                  % "help-to-save-kalculator-jvm" % htsKalcVersion,
    "ai.x"                         %% "play-json-extensions"       % playJsonExtensionsVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"       % jacksonModuleVersion
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
      "org.scalamock" %% "scalamock" % scalaMockVersion % "test"
    )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq.empty

  def testCommon(scope: String): Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % scope,
    "org.pegdown" % "pegdown"                 % pegdownVersion   % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.4.57.v20241219" % scope
  )
}
