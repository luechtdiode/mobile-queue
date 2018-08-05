name := "mobile-queue"

version := "0.1"

connectInput in run := true

lazy val akkaHttpVersion = "10.1.3"

lazy val akkaVersion    = "2.5.14"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "ch.seidel",
	    organizationName := "Roland Seidel",
      licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
      scalaVersion    := "2.12.5"
    )),
    name := "mbq",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster"         % akkaVersion,
  	  "com.typesafe.akka" %% "akka-cluster-tools"   % akkaVersion,
      
      "com.jason-goodwin" %% "authentikat-jwt"      % "0.4.5",
      
      "io.swagger"        % "swagger-jaxrs"         % "1.5.16",
	    "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.14.1",
	  
	    "org.scala-lang"   % "scala-reflect"          % scalaVersion.value,
	  
	    "org.slf4j"        % "slf4j-api"              % "1.7.7",
      "ch.qos.logback"   %  "logback-classic"       % "1.1.3",

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion    % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.3"            % Test,
      "junit"             %  "junit"                % "4.12"             % Test
    )
  )
