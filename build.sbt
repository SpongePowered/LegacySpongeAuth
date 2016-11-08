name := "spongeauth"

version := "1.0"

lazy val `spongeauth` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws, specs2 % Test)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

resolvers ++= Seq(
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  "org.spongepowered"       %%  "sponge-play"             %   "1.0.0-SNAPSHOT",
  "com.typesafe.play"       %%  "play-slick"              %   "2.0.0",
  "com.typesafe.play"       %%  "play-slick-evolutions"   %   "2.0.0",
  "org.postgresql"          %   "postgresql"              %   "9.4.1208.jre7",
  "com.google.zxing"        %   "core"                    %   "3.3.0",
  "org.apache.commons"      %   "commons-io"              %   "1.3.2"
)
