name := "BridgePF"

version := "0.1-SNAPSHOT"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

resolvers += "Sage Ext Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/ext-releases-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "[2014,)",
  "org.sagebionetworks" % "lib-stackConfiguration" % "[2014,)",
  "cglib" % "cglib" % "2.2.2"
)     

play.Project.playJavaSettings
