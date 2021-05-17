import play.core.PlayVersion
import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {

  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest,
    dependencyOverrides := overrides(),
    resolvers += "emueller-bintray" at "https://dl.bintray.com/emueller/maven"
  )

  private val simpleReactiveMongoVersion = "8.0.0-play-27"
  private val bootstrapVersion           = "5.1.0"
  private val domainVersion              = "5.11.0-play-27"
  private val catsCoreVersion            = "2.2.0"
  private val catsParVersion             = "1.0.0-RC2"
  private val scalaUriVersion            = "1.5.1"
  private val playHmrcVersion            = "6.2.0-play-27"
  private val enumeratumVersion          = "1.5.15"
  private val macrosVersion              = "2.3.7"
  private val refinedVersion             = "0.9.4"
  private val htsKalcVersion             = "0.5.0"

  private val scalaMockVersion = "4.1.0"
  private val scalaTestVersion = "3.0.5"

  private val wiremockVersion      = "2.21.0"
  private val scalaTestPlusVersion = "4.0.3"

  private val scalaCheckVersion              = "1.14.0"
  private val pegdownVersion                 = "1.6.0"
  private val playJsonSchemaValidatorVersion = "0.9.5"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"              %% "bootstrap-backend-play-27"  % bootstrapVersion withSources (),
    "uk.gov.hmrc"              %% "domain"                     % domainVersion,
    "org.typelevel"            %% "cats-core"                  % catsCoreVersion,
    "io.chrisdavenport"        %% "cats-par"                   % catsParVersion,
    "io.lemonlabs"             %% "scala-uri"                  % scalaUriVersion,
    "uk.gov.hmrc"              %% "play-hmrc-api"              % playHmrcVersion,
    "uk.gov.hmrc"              %% "simple-reactivemongo"       % simpleReactiveMongoVersion,
    "com.beachape"             %% "enumeratum-play-json"       % enumeratumVersion,
    "com.softwaremill.macwire" %% "macros"                     % macrosVersion,
    "eu.timepit"               %% "refined"                    % refinedVersion,
    "uk.gov.hmrc"              % "help-to-save-kalculator-jvm" % htsKalcVersion
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
      "org.scalamock" %% "scalamock" % scalaMockVersion % "test",
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
    )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
      "com.github.tomakehurst" % "wiremock"            % wiremockVersion      % "it",
      "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % "it"
    )

  def testCommon(scope: String): Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"                 % scalaCheckVersion              % scope,
    "org.pegdown"       % "pegdown"                     % pegdownVersion                 % scope,
    "com.typesafe.play" %% "play-test"                  % PlayVersion.current            % scope,
    "com.eclipsesource" %% "play-json-schema-validator" % playJsonSchemaValidatorVersion % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.2.22.v20170606" % scope
  )

  // Transitive dependencies in scalatest/scalatestplusplay drag in a newer version of jetty that is not
  // compatible with wiremock, so we need to pin the jetty stuff to the older version.
  // see https://groups.google.com/forum/#!topic/play-framework/HAIM1ukUCnI
  val jettyVersion = "9.2.13.v20150730"

  def overrides(): Seq[ModuleID] = Seq(
    "org.eclipse.jetty"           % "jetty-server"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-servlet"      % jettyVersion,
    "org.eclipse.jetty"           % "jetty-security"     % jettyVersion,
    "org.eclipse.jetty"           % "jetty-servlets"     % jettyVersion,
    "org.eclipse.jetty"           % "jetty-continuation" % jettyVersion,
    "org.eclipse.jetty"           % "jetty-webapp"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-xml"          % jettyVersion,
    "org.eclipse.jetty"           % "jetty-client"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-http"         % jettyVersion,
    "org.eclipse.jetty"           % "jetty-io"           % jettyVersion,
    "org.eclipse.jetty"           % "jetty-util"         % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-api"      % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-common"   % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-client"   % jettyVersion
  )
}
