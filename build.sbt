import TestPhases.oneForkedJvmPerTest
import sbt.{CrossVersion, Resolver}
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "mobile-help-to-save"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 90,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(AppDependencies.appDependencies: _*)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.8",
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    PlayKeys.playDefaultPort := 8248,
    // based on https://tpolecat.github.io/2017/04/25/scalac-flags.html but cut down for scala 2.11
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Ypartial-unification",
      "-Ywarn-dead-code",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      //"-Ywarn-unused-import", - does not work well with fatal-warnings because of play-generated sources
      //"-Xfatal-warnings",
      "-Xlint"
    ),
    addCommandAlias("testAll", ";reload;test;it:test")
  )
  .settings(evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false))
  .settings(unmanagedSourceDirectories in Test += baseDirectory.value / "testcommon")
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base =>
      Seq(
        base / "it",
        base / "testcommon"
      )
    ).value: _*
  )
  .settings(
    Keys.fork in IntegrationTest := false,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
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
    resolvers ++= Seq(Resolver.jcenterRepo),
    addCompilerPlugin("org.spire-math"  %% "kind-projector"     % "0.9.9"),
    addCompilerPlugin("com.olegpy"      %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin("org.scalamacros" % "paradise"            % "2.1.1" cross CrossVersion.full)
  )
