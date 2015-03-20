name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  cache,
  filters,
  "com.amazonaws" % "aws-java-sdk-s3" % "1.9.23",
  "com.amazonaws" % "aws-java-sdk-sts" % "1.9.23",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.9.23",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.9.23",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.14.0",
  "org.springframework" % "spring-context" % "4.0.7.RELEASE",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "commons-validator" % "commons-validator" % "1.4.1",
  "commons-io" % "commons-io" % "2.4",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.5.1",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.1",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.1",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.5.1",
  "com.google.guava" % "guava" % "18.0",
  "org.apache.shiro" % "shiro-core" % "1.2.3",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.51",
  "javax.mail" % "mail" % "1.4.7",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC3.1",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC3.1",
  "redis.clients" % "jedis" % "2.6.2",
  "org.xhtmlrenderer" % "flying-saucer-pdf" % "9.0.7",
  javaWs % "test",
  "junit" % "junit" % "4.12" % "test",
  "org.mockito" % "mockito-core" % "1.10.19" % "test",
  "org.springframework" % "spring-test" % "4.0.7.RELEASE" % "test",
  "nl.jqno.equalsverifier" % "equalsverifier" % "1.7.1" % "test"
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)
