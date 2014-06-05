name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "com.amazonaws" % "aws-java-sdk" % "1.7.7",
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.springframework" % "spring-test" % "4.0.3.RELEASE",
  "cglib" % "cglib" % "2.2.2",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "commons-validator" % "commons-validator" % "1.4.0",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13",
  "com.google.guava" % "guava" % "17.0",
  "org.mockito" % "mockito-all" % "1.9.5",
  "org.jasypt" % "jasypt" % "1.9.2",
  "com.github.fge" % "json-schema-validator" % "2.2.3",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  "com.github.detro.ghostdriver" % "phantomjsdriver" % "1.1.0" % "test",
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.beta",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.beta"
)

play.Project.playJavaSettings
