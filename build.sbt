name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  filters,
  "com.amazonaws" % "aws-java-sdk" % "1.9.13",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.12.0",
  "org.springframework" % "spring-context" % "4.0.7.RELEASE",
  "org.springframework" % "spring-test" % "4.0.7.RELEASE",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "commons-validator" % "commons-validator" % "1.4.0",
  "commons-io" % "commons-io" % "2.4",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.4.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.4.2",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.2",
  "com.google.guava" % "guava" % "18.0",
  "org.mockito" % "mockito-all" % "1.9.5",
  "org.apache.shiro" % "shiro-core" % "1.2.3",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.51",
  "com.github.fge" % "json-schema-validator" % "2.2.5",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2.1",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2.1",
  "redis.clients" % "jedis" % "2.6.1",
  "nl.jqno.equalsverifier" % "equalsverifier" % "1.6"
)

// To avoid reloading the Spring context
sbt.Keys.fork in Test := false

generateReverseRouter := false

play.Project.playJavaSettings
