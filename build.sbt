name := "BridgePF"

version := "0.1-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sage Ext Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/ext-releases-local"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

resolvers += "Sage Snapshot Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-snapshots-local"

libraryDependencies ++= Seq(
  cache,
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "[2014,)",
  "cglib" % "cglib" % "2.2.2"
)     

play.Project.playJavaSettings

// Only for development
// "org.sagebionetworks" % "lib-stackConfiguration" % "develop-SNAPSHOT",
