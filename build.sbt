name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  filters,
  "com.amazonaws" % "aws-java-sdk" % "1.8.11",
  "org.springframework" % "spring-context" % "4.0.7.RELEASE",
  "org.springframework" % "spring-test" % "4.0.7.RELEASE",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "commons-validator" % "commons-validator" % "1.4.0",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13",
  "com.google.guava" % "guava" % "18.0",
  "org.mockito" % "mockito-all" % "1.9.5",
  "org.jasypt" % "jasypt" % "1.9.2",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.51",
  "com.github.fge" % "json-schema-validator" % "2.2.5",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC2",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC2",
  "redis.clients" % "jedis" % "2.5.2",
  "com.github.detro.ghostdriver" % "phantomjsdriver" % "1.1.0" % "test"
)

// To avoid reloading the Spring context
sbt.Keys.fork in Test := false

generateReverseRouter := false

play.Project.playJavaSettings
