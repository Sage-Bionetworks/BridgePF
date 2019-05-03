name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Heroku Maven repository" at "http://s3pository.heroku.com/maven-central/"

resolvers += "Bridge Maven repository" at "https://repo-maven.sagebridge.org/"

resolvers += "Synapse repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local/"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  cache,
  filters,
  // Sage packages
  "org.sagebionetworks" % "BridgeServerLogic" % "1.0.3",
  // New Relic
  "com.newrelic.agent.java" % "newrelic-agent" % "3.42.0",
  // Spring
  "org.springframework" % "spring-context" % "4.3.18.RELEASE",
  // Commons-IO is needed to resolve version conflicts
  "commons-io" % "commons-io" % "2.4",

  // Test
  javaWs % Test,
  "junit" % "junit" % "4.12" % Test,
  "org.mockito" % "mockito-core" % "2.23.4" % Test,
  "org.sagebionetworks" % "BridgeTestUtils" % "1.4" % Test,
  "org.springframework" % "spring-test" % "4.3.18.RELEASE" % Test
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

routesGenerator := InjectedRoutesGenerator

testOptions += Tests.Argument(TestFrameworks.JUnit, "-a")

// Compile before generating eclipse files
EclipseKeys.preTasks := Seq(compile in Compile)
// Java project files only
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead 

