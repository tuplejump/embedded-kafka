
import scala.language.postfixOps
import sbt.Keys._
import sbt._
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin

object Build extends sbt.Build {

  lazy val root = project.in(file("."))
    .settings(Settings.common ++ Seq(libraryDependencies ++= Seq(
      Library.akkaActor,
      Library.kafka,
      Library.logback,
      Library.commonsIo,
      Library.Test.scalaCheck % "test, it",
      Library.Test.scalaTest  % "test, it",
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, minor)) if minor < 11 => Library.Cross.slf4j
        case _                              => Library.Cross.scalaLogging
      }
    )))
  .enablePlugins(AutomateHeaderPlugin) configs IntegrationTest

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
  //import com.scalapenos.sbt.prompt.SbtPrompt.autoImport.{promptTheme, ScalapenosTheme}

  val versionStatus = settingKey[Unit]("The Scala version used in cross-build reapply for '+ package', '+ publish'.")

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

    crossScalaVersions := Version.Scala,

    crossVersion := CrossVersion.binary,

    scalaVersion := sys.props.getOrElse("scala.version", crossScalaVersions.value.head),

    versionStatus := Version.cross(scalaVersion.value),

    HeaderPlugin.autoImport.headers := Map(
      "scala" -> Apache2_0("2016", "Tuplejump"),
      "conf"  -> Apache2_0("2016", "Tuplejump", "#")
    )
  )

  val encoding = Seq("-encoding", "UTF-8")

  lazy val common = buildSettings ++ testSettings ++ styleSettings ++ Seq(

    cancelable in Global := true,

    crossPaths in ThisBuild := true,

    //GitPlugin.autoImport.git.useGitDescribe := true,
    //promptTheme := ScalapenosTheme,

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
      "-source", Version.JavaBinary,
      "-target", Version.JavaBinary,
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
