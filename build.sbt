name := "BridgePF"

version := "0.1-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sage Ext Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/ext-releases-local"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

resolvers += "Sage Snapshot Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-snapshots-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "2014-04-23-1152-e52b875",
  "org.sagebionetworks" % "lib-stackConfiguration" % "2014-04-23-1152-e52b875",
  "cglib" % "cglib" % "2.2.2",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13"
)

play.Project.playJavaSettings

// Production
// "org.sagebionetworks" % "synapseJavaClient" % "[2014,)",

// Dev
// "org.sagebionetworks" % "synapseJavaClient" % "develop-SNAPSHOT",
// "org.sagebionetworks" % "lib-stackConfiguration" % "develop-SNAPSHOT",
