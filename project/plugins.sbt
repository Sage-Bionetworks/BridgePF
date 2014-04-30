// Comment to get more information during initialization
logLevel := Level.Warn

// Insert the Heroku repository before Typesafe
resolvers += "heroku-central" at "http://s3pository.heroku.com/maven-central/"

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.2")