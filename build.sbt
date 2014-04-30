name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.springframework" % "spring-test" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "2014-04-23-1152-e52b875",
  "cglib" % "cglib" % "2.2.2",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13"
)

play.Project.playJavaSettings

val gruntRelease = taskKey[Unit]("Run the 'grunt release' task to build JavaScript/CSS.")

gruntRelease := {
  "grunt -b public --gruntfile public/Gruntfile.js release" !
}

(compile in Compile) <<= (compile in Compile) dependsOn gruntRelease
