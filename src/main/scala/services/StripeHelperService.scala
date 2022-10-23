package services

import akka.actor.ActorSystem
import com.stripe.model.Event
import com.stripe.net.Webhook

import scala.concurrent.{ExecutionContext, Future}

class StripeHelperService(endpointSecret: String)(
  implicit val system: ActorSystem,
  executionContext: ExecutionContext
) {

  def handleStripeBody(sigHeader: String, payload: String): Future[Event] = {
    Future {
      Webhook.constructEvent(payload, sigHeader, endpointSecret)
    }
  }

}
