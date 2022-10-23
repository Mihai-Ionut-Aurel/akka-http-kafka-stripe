import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory
import org.apache.kafka.common.serialization.StringSerializer
import routes.StripeRoute
import org.testcontainers.containers.KafkaContainer
import producers.StripeBareProducer
import services.StripeHelperService

import scala.concurrent.ExecutionContext

object StripeApp extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val executor: ExecutionContext = system.dispatcher

  val config = ConfigFactory.load()
  val logger = Logging(system, "StripeApp")
  lazy val stripeProducer: StripeBareProducer[String, String] = {

    new StripeBareProducer[String, String](
      logger,
      config.getString("kafka.bootstrap-servers"),
      new StringSerializer,
      new StringSerializer
    )
  }

  val stripeService = new StripeHelperService(
    config.getString("stripe.endpoint-secret")
  )
  val stripeRoutes =
    new StripeRoute(logger, stripeService, stripeProducer)

  Http()
    .newServerAt(config.getString("http.interface"), config.getInt("http.port"))
    .bindFlow(stripeRoutes.routes)
}
