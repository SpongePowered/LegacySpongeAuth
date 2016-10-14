name := "spongesso"

version := "1.0"

lazy val `spongesso` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws, specs2 % Test)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  "com.typesafe.play"       %%  "play-slick"              %   "2.0.0",
  "com.typesafe.play"       %%  "play-slick-evolutions"   %   "2.0.0",
  "org.postgresql"          %   "postgresql"              %   "9.4.1208.jre7",
  "com.github.tminglei"     %%  "slick-pg"                %   "0.12.0",
  "org.mindrot"             %   "jbcrypt"                 %   "0.3m",
  "org.scalatestplus.play"  %%  "scalatestplus-play"      %   "1.5.0" % "test"
)
