
import sbt.Resolver
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}

val appName = "mobile-help-to-save"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.SystemId;.*\.IndexedMongoRepo;.*\.AppLoader;.*\.ScalaUriConfig;.*\.Routes;.*\.RoutesPrefix;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(AppDependencies.appDependencies: _*)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.13.12",
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    PlayKeys.playDefaultPort := 8248,
    // based on https://tpolecat.github.io/2017/04/25/scalac-flags.html but cut down for scala 2.11
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-Xlint",
      "-Ymacro-annotations"
    ),
    addCommandAlias("testAll", ";reload;test;it:test")
  )
  .settings(update / evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false))
  .settings(Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon")
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    IntegrationTest / unmanagedSourceDirectories := (IntegrationTest / baseDirectory)(base =>
      Seq(
        base / "it",
        base / "testcommon"
      )
    ).value: _*
  )
  .settings(
    IntegrationTest / Keys.fork := false,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / parallelExecution := false
  )
  .settings(
    routesImport ++= Seq(
      "uk.gov.hmrc.domain._",
      "uk.gov.hmrc.mobilehelptosave.binders.Binders._",
      "uk.gov.hmrc.mobilehelptosave.domain.types._",
      "uk.gov.hmrc.mobilehelptosave.domain.types.ModelTypes._"
    )
  )
  .settings(
    resolvers ++= Seq(Resolver.jcenterRepo, Resolver.bintrayRepo("hmrc-mobile", "mobile-releases")),
    addCompilerPlugin("org.typelevel"  %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.3.1")
  )
