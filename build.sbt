import Dependencies.Compile._
import Dependencies.Test._

ThisBuild / scalaVersion := "3.1.2"
ThisBuild / organization := "com.47deg"
ThisBuild / versionScheme := Some("early-semver")

addCommandAlias(
  "plugin-example",
  "reload; clean; publishLocal; continuationsPluginExample/compile")
addCommandAlias("ci-test", "scalafmtCheckAll; scalafmtSbtCheck; github; mdoc; root / test")
addCommandAlias("ci-it-test", "continuationsPlugin / IntegrationTest / test")
addCommandAlias("ci-docs", "github; mdoc")
addCommandAlias("ci-publish", "github; ci-release")

publish / skip := true

logBuffered := false

lazy val root = // I
  (project in file("./")).aggregate(
    benchmarks, // A
    continuationsPlugin, // C
    // continuationsPluginExample, // D
    documentation, // E
    `http-scala-fx`, // F
    `java-net-multipart-body-publisher`, // G
    `munit-scala-fx`, // H
    `scala-fx`, // J
    `scalike-jdbc-scala-fx`, // K
    `sttp-scala-fx`, // L
    // `zero-arguments-no-continuation-treeview`,
    // `zero-arguments-one-continuation-code-before-used-after`,
    // `list-map`,
    // `two-arguments-two-continuations`,
    `munit-snap`,
    `suspend-in-lambda`,
    `test-nested-continuations`
  )

lazy val bypassZinc = (project in file("./bypassZinc")).aggregate(
  continuationsPluginExample,
  `zero-arguments-no-continuation-treeview`,
  `zero-arguments-one-continuation-code-before-used-after`,
  `list-map`,
  `two-arguments-two-continuations`
)

lazy val `scala-fx` = project.settings(scalafxSettings: _*)

lazy val `test-nested-continuations` = project
  .in(file("./test-nested-continuations"))
  .settings(continuationsPluginExampleShowTreeSettings)
  .settings(forceCompilation := false)
  .enablePlugins(ForceableCompilationPlugin).dependsOn(continuationsPlugin)

lazy val continuationsPlugin = project
  .configs(IntegrationTest)
  .settings(
    continuationsPluginSettings: _*
  )
  .dependsOn(`munit-snap`)

lazy val continuationsPluginExample = project
  .dependsOn(continuationsPlugin)
  .settings(
    continuationsPluginExampleSettings: _*
  )
  .enablePlugins(ForceableCompilationPlugin)

lazy val `suspend-in-lambda` = (project in file("./suspend-in-lambda"))
  .settings(continuationsPluginExampleShowTreeSettings: _*)
  .dependsOn(continuationsPlugin)

lazy val `zero-arguments-no-continuation-treeview` =
  (project in file("./zero-arguments-no-continuation-treeview"))
    .settings(continuationsPluginExampleShowTreeSettings: _*)
    .dependsOn(continuationsPlugin)
    .enablePlugins(ForceableCompilationPlugin)

lazy val `zero-arguments-one-continuation-code-before-used-after` =
  (project in file("./zero-arguments-one-continuation-code-before-used-after"))
    .settings(continuationsPluginExampleShowTreeSettings: _*)
    .dependsOn(continuationsPlugin)
    .enablePlugins(ForceableCompilationPlugin)

lazy val `list-map` = (project in file("./list-map"))
  .settings(continuationsPluginExampleShowTreeSettings: _*)
  .dependsOn(continuationsPlugin)
  .enablePlugins(ForceableCompilationPlugin)

lazy val `two-arguments-two-continuations` =
  (project in file("./two-arguments-two-continuations"))
    .settings(continuationsPluginExampleShowTreeSettings: _*)
    .dependsOn(continuationsPlugin)
    .enablePlugins(ForceableCompilationPlugin)

lazy val benchmarks =
  project
    .dependsOn(`scala-fx`, continuationsPlugin)
    .settings(
      publish / skip := true,
      libraryDependencies += catsEffect // libraryDependencies += zio
      // libraryDependencies += kyo
      // , libraryDependencies += oxd
    )
    .settings(continuationsPluginExampleSettings)
    .enablePlugins(JmhPlugin)

lazy val documentation = project
  .dependsOn(`scala-fx`)
  .enablePlugins(MdocPlugin)
  .settings(mdocOut := file("."))
  .settings(publish / skip := true)

lazy val `munit-scala-fx` = (project in file("./munit-scalafx"))
  .configs(IntegrationTest)
  .settings(
    munitScalaFXSettings
  )
  .dependsOn(`scala-fx`)

lazy val `cats-scala-fx` = (project in file("./cats-scalafx"))
  .configs(IntegrationTest)
  .settings(
    catsScalaFXSettings
  )
  .dependsOn(`scala-fx`)

lazy val `scalike-jdbc-scala-fx` = project
  .dependsOn(`scala-fx`, `munit-scala-fx` % "test -> compile")
  .settings(publish / skip := true)
  .settings(scalalikeSettings)

lazy val `java-net-multipart-body-publisher` =
  (project in file("./java-net-mulitpart-body-publisher")).settings(commonSettings)

lazy val `http-scala-fx` = (project in file("./http-scala-fx"))
  .settings(httpScalaFXSettings)
  .settings(generateMediaTypeSettings)
  .dependsOn(
    `java-net-multipart-body-publisher`,
    `scala-fx`,
    `munit-scala-fx` % "test -> compile")
  .enablePlugins(HttpScalaFxPlugin)

lazy val `sttp-scala-fx` = (project in file("./sttp-scala-fx"))
  .settings(sttpScalaFXSettings)
  .dependsOn(
    `java-net-multipart-body-publisher`,
    `scala-fx`,
    `http-scala-fx`,
    `munit-scala-fx` % "test -> compile")

lazy val `munit-snap` = (project in file("./munit-snap")).settings(munitSnapSettings)

lazy val munitSnapSettings = Seq(
  name := "munit-snap",
  autoAPIMappings := true,
  Test / fork := true,
  libraryDependencies += munit,
  libraryDependencies += circe,
  libraryDependencies += circeParser,
  libraryDependencies += circeGeneric % Test
)

lazy val commonSettings = Seq(
  javaOptions ++= javaOptionsSettings,
  autoAPIMappings := true,
  Test / fork := true
)

lazy val scalafxSettings: Seq[Def.Setting[_]] =
  Seq(
    classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    javaOptions ++= javaOptionsSettings,
    autoAPIMappings := true,
    libraryDependencies ++= Seq(
      scalacheck % Test
    )
  )

def testAndIntegrationTest(m: ModuleID): List[ModuleID] = List(m).flatMap { m =>
  List(m % Test, m % IntegrationTest)
}

lazy val continuationsPluginSettings: Seq[Def.Setting[_]] =
  Defaults.itSettings ++ Seq(
    exportJars := true,
    autoAPIMappings := true,
    Test / fork := true,
    libraryDependencies ++= List(
      "org.scala-lang" %% "scala3-compiler" % "3.1.2"
    ) ++ testAndIntegrationTest(munit),
    Test / scalacOptions += "-Ydebug:continuations",
    Test / javaOptions += {
      val `scala-compiler-classpath` =
        (Compile / dependencyClasspath)
          .value
          .files
          .map(_.toPath().toAbsolutePath().toString())
          .mkString(":")
      s"-Dscala-compiler-classpath=${`scala-compiler-classpath`}"
    },
    Test / javaOptions += {
      s"""-Dcompiler-scalacOptions=\"${(Test / scalacOptions).value.mkString(" ")}\""""
    },
    Test / javaOptions += Def.taskDyn {
      Def.task {
        val _ = (Compile / Keys.`package`).value
        val `scala-compiler-options` =
          s"${(continuationsPlugin / Compile / packageBin).value}"
        s"""-Dscala-compiler-plugin=${`scala-compiler-options`}"""
      }
    }.value,
    IntegrationTest / fork := true,
    IntegrationTest / javaOptions := (Test / javaOptions).value
  )

lazy val continuationsPluginExampleShowTreeSettings: Seq[Def.Setting[_]] =
  Seq(
    fork := false,
    publish / skip := true,
    autoCompilerPlugins := true,
    resolvers += Resolver.mavenLocal,
    forceCompilation := true,
    Compile / scalacOptions += s"-Xplugin:${(continuationsPlugin / Compile / packageBin).value}",
    Compile / scalacOptions += "-Ydebug:continuations",
    Compile / scalacOptions += "-Xprint:all",
    Test / scalacOptions += s"-Xplugin:${(continuationsPlugin / Compile / packageBin).value}",
    Test / scalacOptions += "-Xprint:repeatableAnnotations"
  )

lazy val continuationsPluginExampleSettings: Seq[Def.Setting[_]] =
  Seq(
    publish / skip := true,
    autoCompilerPlugins := true,
    forceCompilation := true,
    resolvers += Resolver.mavenLocal,
    Compile / scalacOptions += s"-Xplugin:${(continuationsPlugin / Compile / packageBin).value}",
    Test / scalacOptions += s"-Xplugin:${(continuationsPlugin / Compile / packageBin).value}"
  )

lazy val munitScalaFXSettings = Defaults.itSettings ++ Seq(
  libraryDependencies ++= Seq(
    munitScalacheck,
    hedgehog,
    junit,
    munit,
    junitInterface
  )
) ++ commonSettings

lazy val catsScalaFXSettings = Seq(
  classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  javaOptions ++= javaOptionsSettings,
  autoAPIMappings := true,
  libraryDependencies ++= Seq(
    catsEffect,
    scalacheck % Test
  )
)

lazy val scalalikeSettings: Seq[Def.Setting[_]] =
  Seq(
    classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    javaOptions ++= javaOptionsSettings,
    autoAPIMappings := true,
    libraryDependencies ++= Seq(
      scalikejdbc,
      h2Database,
      logback,
      postgres,
      scalacheck % Test,
      testContainers % Test,
      testContainersMunit % Test,
      testContainersPostgres % Test,
      flyway % Test
    )
  )

lazy val httpScalaFXSettings = commonSettings

lazy val sttpScalaFXSettings = commonSettings ++ Seq(
  libraryDependencies += sttp,
  libraryDependencies += httpCore5,
  libraryDependencies += hedgehog % Test
)

lazy val javaOptionsSettings = Seq(
  "-XX:+IgnoreUnrecognizedVMOptions",
  "-XX:-DetectLocksInCompiledFrames",
  "-XX:+UnlockDiagnosticVMOptions",
  "-XX:+UnlockExperimentalVMOptions",
  "-XX:+UseNewCode",
  "--add-modules=java.base",
  "--add-modules=jdk.incubator.concurrent",
  "--add-opens java.base/jdk.internal.vm=ALL-UNNAMED",
  "--add-exports java.base/jdk.internal.vm=ALL-UNNAMED",
  "--enable-preview"
)
