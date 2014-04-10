name := "BridgePF"

version := "0.1-SNAPSHOT"

resolvers += "Sage Snapshot Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-snapshots-local"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

resolvers += "Sage Ext Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/ext-releases-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "develop-SNAPSHOT",
  "org.sagebionetworks" % "lib-stackConfiguration" % "develop-SNAPSHOT",
  "cglib" % "cglib" % "2.2.2",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13"
)     

play.Project.playJavaSettings
