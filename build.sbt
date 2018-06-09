import TestPhases.oneForkedJvmPerTest
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

name := "mobile-help-to-save"

lazy val root = (project in file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(addCommandAlias("testAll", ";clean;reload;update;test;it:test"))

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

scoverageSettings
scalaSettings
publishingSettings
unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
defaultSettings()

PlayKeys.playDefaultPort := 8248

// from https://github.com/typelevel/cats/blob/master/README.md
scalacOptions += "-Ypartial-unification"

AppDependencies.appDependencies
retrieveManaged := true
evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)

unmanagedSourceDirectories in Test += baseDirectory.value / "testcommon"

Keys.fork in IntegrationTest := false
unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(
  base / "it",
  base / "testcommon"
)).value
addTestReportOption(IntegrationTest, "int-test-reports")
testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value)
parallelExecution in IntegrationTest := false

resolvers ++= Seq(
  Resolver.bintrayRepo("hmrc", "releases"),
  Resolver.jcenterRepo
)


