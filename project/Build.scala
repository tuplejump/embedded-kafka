
import scala.language.postfixOps
import sbt.Keys._
import sbt._
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin

object Build extends sbt.Build {

  final val Akka_Scala211   = "2.4.1"
  final val Akka_Scala210   = "2.3.14"

  lazy val root = project.in(file("."))
    .settings(Settings.common ++ Seq(libraryDependencies ++=
      Seq(
        "org.apache.kafka"               %% "kafka"          % "0.9.0.1" excludeAll(kafkaExcludes: _*),
        "ch.qos.logback"                 % "logback-classic" % "1.0.7",
        "commons-io"                     %  "commons-io"     % "2.4",
        "org.scalacheck"                 %% "scalacheck"     % "1.12.5"    % "test, it",
        "org.scalatest"                  %% "scalatest"      % "2.2.5"     % "test, it"
      ) ++
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, minor)) if minor < 11 =>
          Seq(
            "com.typesafe.akka"          %% "akka-actor"   % Akka_Scala210,
            "com.typesafe.akka"          %% "akka-testkit" % Akka_Scala210 % "test, it",
            "org.slf4j"                  % "slf4j-api"     % "1.7.13")
        case _ =>
          Seq(
            "com.typesafe.akka"          %% "akka-actor"    % Akka_Scala211,
            "com.typesafe.akka"          %% "akka-testkit"  % Akka_Scala211 % "test, it",
            "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0")
      })))
  .enablePlugins(AutomateHeaderPlugin) configs IntegrationTest

  val kafkaExcludes = Seq(
    ExclusionRule("com.sun.jmx", "jmxri"),
    ExclusionRule("com.sun.jdmk", "jmxtools"),
    ExclusionRule("net.sf.jopt-simple", "jopt-simple"),
    ExclusionRule("org.slf4j", "slf4j-simple"),
    ExclusionRule("org.slf4j", "slf4j-log4j12")
  )

}

object Settings extends sbt.Build {
  import de.heikoseeberger.sbtheader.HeaderPlugin
  import de.heikoseeberger.sbtheader.license.Apache2_0
  import wartremover.WartRemover.autoImport._
  import com.typesafe.tools.mima.plugin.MimaKeys._
  import com.typesafe.tools.mima.plugin.MimaPlugin._
  import org.scalastyle.sbt.ScalastylePlugin._
  import scoverage.ScoverageKeys
  //import com.typesafe.sbt.GitPlugin

  val versionStatus = settingKey[Unit]("The Scala version used in cross-build reapply for '+ package', '+ publish'.")

  final val javaVersion = scala.util.Properties.javaVersion

  final val javaBinaryVersion = javaVersion.dropRight(5)

  lazy val buildSettings = Seq(
    name := "embedded-kafka",
    organization := "com.tuplejump",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("http://github.com/tuplejump/embedded-kafka")),

    pomExtra :=
      <scm>
        <url>git@github.com:tuplejump/embedded-kafka.git</url>
        <connection>scm:git:git@github.com:tuplejump/embedded-kafka.git</connection>
      </scm>
        <developers>
          <developer>
            <id>helena</id>
            <name>Helena Edelson</name>
          </developer>
        </developers>,

    crossScalaVersions := Seq("2.11.7", "2.10.5"),

    crossVersion := CrossVersion.binary,

    scalaVersion := sys.props.getOrElse("scala.version", crossScalaVersions.value.head),

    versionStatus := {
        println(s"Scala: ${scalaVersion.value} Java: $javaVersion")
    },

    HeaderPlugin.autoImport.headers := Map(
      "scala" -> Apache2_0("2016", "Tuplejump"),
      "conf"  -> Apache2_0("2016", "Tuplejump", "#")
    )
  )

  val encoding = Seq("-encoding", "UTF-8")

  lazy val common = buildSettings ++ testSettings ++ styleSettings ++ Seq(

    cancelable in Global := true,

    crossPaths in ThisBuild := true,

    logBuffered in Compile := false,
    logBuffered in Test := false,

    outputStrategy := Some(StdoutOutput),

    ScoverageKeys.coverageHighlighting := true,

    aggregate in update := false,

    updateOptions := updateOptions.value.withCachedResolution(true),

    incOptions := incOptions.value.withNameHashing(true),

    scalacOptions ++= encoding ++ Seq(
      "-Xfatal-warnings",
      "-deprecation",
      "-feature",
      "-language:_",
      "-unchecked",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code" // N.B. doesn't work well with the ??? hole
      /* "-Ywarn-numeric-widen",
         "-Ywarn-value-discard") */
    ),

    scalacOptions ++= (
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, minor)) if minor < 11 => Seq.empty
        case _ => Seq("-Ywarn-unused-import")
      }),

    javacOptions ++= encoding ++ Seq(
      "-source", javaBinaryVersion,
      "-target", javaBinaryVersion,
      "-Xmx1G",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),

    autoCompilerPlugins := true,
    autoAPIMappings := true,
    //exportJars := true

    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },

    ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet,

    evictionWarningOptions in update := EvictionWarningOptions.default
      .withWarnTransitiveEvictions(false)
      .withWarnDirectEvictions(false)
      .withWarnScalaVersionEviction(false),

    crossPaths in ThisBuild := true,

    wartremoverErrors in (Compile, compile) :=
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, minor)) if minor < 11 =>
          Seq.empty[wartremover.Wart]
        case _ =>
          Warts.allBut(
            Wart.Any,//todo exclude actor
            Wart.Throw,
            Wart.DefaultArguments,
            Wart.NonUnitStatements,
            Wart.Nothing,
            Wart.ToString)
      }),

    parallelExecution in ThisBuild := false,
    parallelExecution in Global := false,

    publishMavenStyle := false,

    managedSourceDirectories := (scalaSource in Compile).value :: Nil,
    unmanagedSourceDirectories := (scalaSource in Compile).value :: Nil
  )

  val compileScalastyle = taskKey[Unit]("compileScalastyle")

  val testScalastyle = taskKey[Unit]("testScalastyle")

  lazy val styleSettings = Seq(
    testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value,
    scalastyleFailOnError := true,
    compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value
  )

  val testConfigs = inConfig(Test)(Defaults.testTasks) ++ inConfig(IntegrationTest)(Defaults.itSettings)

  val testOptionSettings = Seq(
    Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )

  lazy val testSettings = testConfigs ++ Seq(
    parallelExecution in Test := false,
    parallelExecution in IntegrationTest := false,
    testOptions in Test ++= testOptionSettings,
    testOptions in IntegrationTest ++= testOptionSettings,
    fork in Test := true,
    fork in IntegrationTest := true,
    (compile in IntegrationTest) <<= (compile in Test, compile in IntegrationTest) map { (_, c) => c },
    (internalDependencyClasspath in IntegrationTest) <<= Classpaths.concat(internalDependencyClasspath in IntegrationTest, exportedProducts in Test)
  )

  lazy val mimaSettings = mimaDefaultSettings ++ Seq(
    previousArtifact := None,
    binaryIssueFilters ++= Seq.empty
  )

}
