import play.core.PlayVersion
import sbt.Keys._
import sbt.{ModuleID, _}

object AppDependencies {

  def appDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies ++= compile ++ test ++ integrationTest,
    dependencyOverrides := overrides(),
    resolvers += "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
  )

  private val reactiveMongoVersion = "7.20.0-play-26"

  private val enumeratumVersion = "1.5.15"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"              %% "bootstrap-play-26"    % "0.36.0" withSources (),
    "uk.gov.hmrc"              %% "domain"               % "5.6.0-play-26",
    "org.typelevel"            %% "cats-core"            % "1.6.0",
    "io.chrisdavenport"        %% "cats-par"             % "0.2.0",
    "io.lemonlabs"             %% "scala-uri"            % "1.4.1",
    "uk.gov.hmrc"              %% "play-hmrc-api"        % "3.4.0-play-26",
    "uk.gov.hmrc"              %% "simple-reactivemongo" % reactiveMongoVersion,
    "com.beachape"             %% "enumeratum-play-json" % enumeratumVersion,
    "com.softwaremill.macwire" %% "macros"               % "2.3.1"
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % "4.1.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
    "com.github.tomakehurst" % "wiremock"            % "2.21.0" % "it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"  % "it"
  )

  def testCommon(scope: String): Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"                 % "1.14.0"            % scope,
    "org.pegdown"       % "pegdown"                     % "1.6.0"             % scope,
    "com.typesafe.play" %% "play-test"                  % PlayVersion.current % scope,
    "com.eclipsesource" %% "play-json-schema-validator" % "0.9.4"             % scope,
    // workaround for version clash in IntelliJ where without this line both jetty-util-9.2.15.v20160210 and jetty-util-9.2.22.v20170606 are brought in
    // which results in a NoSuchMethodError when running StartupISpec
    "org.eclipse.jetty.websocket" % "websocket-client" % "9.2.22.v20170606" % scope
  )

  // Transitive dependencies in scalatest/scalatestplusplay drag in a newer version of jetty that is not
  // compatible with wiremock, so we need to pin the jetty stuff to the older version.
  // see https://groups.google.com/forum/#!topic/play-framework/HAIM1ukUCnI
  val jettyVersion = "9.2.13.v20150730"
  def overrides(): Set[ModuleID] = Set(
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
