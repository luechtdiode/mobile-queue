name := "mobile-queue"

version := "0.1"

lazy val akkaHttpVersion = "10.0.10"
lazy val akkaVersion    = "2.5.4"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.example",
      scalaVersion    := "2.12.3"
    )),
    name := "mobmomy",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster"         % akkaVersion,
  	  "com.typesafe.akka" %% "akka-cluster-tools"   % akkaVersion,

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion    % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.1"            % Test,
      "junit"             %  "junit"                % "4.12"             % Test,
      
      "io.swagger"        % "swagger-jaxrs"         % "1.5.16",
	  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.11.0",
	  "org.scala-lang"   % "scala-reflect"          % scalaVersion.value,
	  "org.slf4j"        % "slf4j-api"              % "1.7.7",
      "ch.qos.logback"   %  "logback-classic"       % "1.1.3"
    )
  )
