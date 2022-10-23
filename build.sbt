import sbt.librarymanagement.ConflictWarning

enablePlugins(JavaAppPackaging)

name := "scala-stripe"
organization := "com.softpuppets"
version := "1.0"
scalaVersion := "2.13.8"

conflictWarning := ConflictWarning.disable

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")
resolvers += "confluent" at "https://packages.confluent.io/maven/"

libraryDependencies ++= {
  val akkaHttpV = "10.2.9"
  val akkaV = "2.6.19"
  val scalaTestV = "3.2.14"
  val circeV = "0.14.2"
  val akkaHttpCirceV = "1.39.2"
  val stripeJavaV = "21.8.0"
  val kafkaClientV = "2.6.0"
  val avroSerializerV = "7.2.1"
  val avro4sV = "4.1.0"

  val AlpakkaV = "3.0.4"
  val AlpakkaKafkaV = "2.0.5"
  val embeddedKafkaVersionV = "3.2.1"
  val mokitoV = "2.8.47"
  Seq(
    "io.circe" %% "circe-core" % circeV,
    "io.circe" %% "circe-parser" % circeV,
    "io.circe" %% "circe-generic" % circeV,
    "org.scalatest" %% "scalatest" % scalaTestV % Test,
    "org.scalatestplus" %% "mockito-4-6" % "3.2.14.0" % Test,
    "io.github.embeddedkafka" %% "embedded-kafka" % embeddedKafkaVersionV % Test,
    "org.mockito" % "mockito-core" % mokitoV % Test
  ) ++ Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
  ) ++ Seq("com.stripe" % "stripe-java" % stripeJavaV) ++ Seq(
    "org.apache.kafka" % "kafka-clients" % kafkaClientV,
    "io.confluent" % "kafka-avro-serializer" % avroSerializerV,
    "com.sksamuel.avro4s" %% "avro4s-core" % avro4sV,
    "com.lightbend.akka" %% "akka-stream-alpakka-csv" % AlpakkaV,
    "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaV,
    "org.testcontainers" % "kafka" % "1.16.0"
  )
}

Revolver.settings
