import TestPhases.oneForkedJvmPerTest
import sbt.CrossVersion
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "mobile-help-to-save"

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

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(AppDependencies.appDependencies: _*)
  .settings(
    majorVersion := 0,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    PlayKeys.playDefaultPort := 8248,
    // from https://github.com/typelevel/cats/blob/master/README.md
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Ypartial-unification",
      "-Ywarn-dead-code",                 
      "-Ywarn-value-discard",
      "-Ywarn-inaccessible",             
      "-Ywarn-infer-any",            
      "-Ywarn-nullary-override",       
      "-Ywarn-nullary-unit",            
      "-Ywarn-numeric-widen",             
      //"-Ywarn-unused-import",            
      "-Xlint"
    ),
    addCommandAlias("testAll", ";reload;test;it:test")
  )
  .settings(evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false))
  .settings(unmanagedSourceDirectories in Test += baseDirectory.value / "testcommon"
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
      unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(
      base / "it",
      base / "testcommon"
    )).value: _*
  )
  .settings(
    Keys.fork in IntegrationTest := false,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
  )
  .settings(
    resolvers ++= Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.jcenterRepo
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )
