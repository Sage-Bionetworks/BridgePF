name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.2"

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Make sure the Heroku repository is at the very beginning
resolvers += "heroku-maven" at "http://s3pository.heroku.com/maven-central/"

libraryDependencies ++= Seq(
  cache,
  filters,
  // AWS
  "com.amazonaws" % "aws-java-sdk-s3" % "1.9.38",
  "com.amazonaws" % "aws-java-sdk-sts" % "1.9.38",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.9.38",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.9.38",
  // New Relic
  "com.newrelic.agent.java" % "newrelic-agent" % "3.16.1",
  // Spring
  "org.springframework" % "spring-context" % "4.0.7.RELEASE",
  // Apache Commons
  "org.apache.commons" % "commons-lang3" % "3.4",
  "commons-validator" % "commons-validator" % "1.4.1",
  "commons-io" % "commons-io" % "2.4",
  // Jackson
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.5.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.3",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.5.3",
  // Guava
  "com.google.guava" % "guava" % "18.0",
  // Security
  "org.apache.shiro" % "shiro-core" % "1.2.3",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.52",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
  // Quartz
  "org.quartz-scheduler" % "quartz" % "2.2.1",
  // Mail
  "javax.mail" % "mail" % "1.4.7",
  // Joda-Time
  "joda-time" % "joda-time" % "2.7",
  // Stormpath
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC4.2",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC4.2",
  // Redis
  "redis.clients" % "jedis" % "2.7.2",
  // PDF
  "org.xhtmlrenderer" % "flying-saucer-pdf" % "9.0.7",
  // Test
  javaWs % Test,
  "junit" % "junit" % "4.12" % Test,
  "org.mockito" % "mockito-core" % "1.10.19" % Test,
  "org.springframework" % "spring-test" % "4.0.7.RELEASE" % Test,
  "nl.jqno.equalsverifier" % "equalsverifier" % "1.7.1" % Test
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)
