package routes

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.google.gson.Gson
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.{Event, StripeObject}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder, HCursor}
import org.apache.kafka.clients.producer.RecordMetadata
import producers.StripeBareProducer
import services.StripeHelperService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.OptionConverters._
import scala.language.postfixOps

trait StripeProtocols extends ErrorAccumulatingCirceSupport {

  implicit val eventDecoder: Decoder[Event] = new Decoder[Event] {
    final def apply(c: HCursor): Decoder.Result[Event] = {
      new Gson().fromJson(c.root.toString, classOf[Event])
    }
  }

  implicit val eventEncoder: Encoder[Event] = deriveEncoder

}

class StripeRoute(
  logger: LoggingAdapter,
  stripeHelperService: StripeHelperService,
  stripeProducer: StripeBareProducer[String, String]
)(implicit val system: ActorSystem, executionContext: ExecutionContext)
    extends StripeProtocols {

  val failedEventsTopic: String = "failed_events"

  val routes: Route = {
    logRequestResult("scala-stripe") {
      pathPrefix("stripe_webhooks") {
        (post & headerValueByName("Stripe-Signature") & extractRequest) {
          (signature, request) =>
            complete {
              val entityAsText: Future[String] =
                request.entity.toStrict(1 second).map(_.data.utf8String)
              entityAsText.map { requestBody =>
                {
                  val event =
                    stripeHelperService.handleStripeBody(signature, requestBody)

                  val result = event
                    .map(handleStripeEvent)
                    .flatten
                    .map[ToResponseMarshallable](_.toString)
                    .recover[ToResponseMarshallable] {
                      case ex: SignatureVerificationException =>
                        logger.error(
                          s"Failed to verify signature for request id: ${ex.getRequestId} with header ${ex.getSigHeader}."
                        )
                        stripeProducer
                          .produce(
                            failedEventsTopic,
                            ex.getRequestId,
                            requestBody
                          )
                        BadRequest -> ex.getMessage
                      case ex: Throwable =>
                        logger.error(s"Encountered error: ${ex.getMessage}")
                        InternalServerError -> ex.getMessage
                    }
                  result
                }
              }
            }
        }
      }
    }
  }

  def handleStripeEvent(event: Event): Future[RecordMetadata] = {
    // Check if the deserialized  nested object inside the event is present
    try {
      val stripeObject: Option[StripeObject] =
        event.getDataObjectDeserializer.getObject.toScala
      stripeObject match {
        case Some(_) =>
          stripeProducer
            .produce(event.getType, event.getId, event.toJson)
        case None =>
          Future.failed(
            new Exception("The stripe object from the event was null")
          )
      }
    } catch {
      case ex: Throwable =>
        Future.failed(
          new Exception(
            s"Encountered error while trying to retrieve and produce the object of the event: ${ex.getMessage}"
          )
        );
    }

  }
}
