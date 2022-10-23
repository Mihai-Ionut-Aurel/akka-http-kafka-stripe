import akka.event.NoLogging
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.google.gson.Gson
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import producers.StripeBareProducer
import routes.StripeRoute
import services.StripeHelperService

import scala.concurrent.Future

class StripeRouteSpec
    extends AsyncFlatSpec
    with Matchers
    with ScalatestRouteTest
    with MockitoSugar {
  override def testConfigSource = "akka.loglevel = WARNING"
  def config = testConfig
  val logger = NoLogging

  val stripeServiceMock = mock[StripeHelperService]
  val stripeProducerMock: StripeBareProducer[String, String] =
    mock[StripeBareProducer[String, String]]
  val stripeRoute =
    new StripeRoute(logger, stripeServiceMock, stripeProducerMock)

  val emptyObjectJson =
    """{
      |  "account": null,
      |  "api_version": "2022-08-01",
      |  "created": 1665320999,
      |  "data": {
      |    "previous_attributes": null,
      |    "object": {
      |    }
      |  },
      |  "id": "evt_1LqzGWA7E0whzXsQe1rk1n2B",
      |  "livemode": false,
      |  "object": "event",
      |  "pending_webhooks": 2,
      |  "request": {
      |    "id": "req_qnFe3HlWNrA4dt",
      |    "idempotency_key": "90322d71-d198-4485-a274-196c0fed4c25"
      |  },
      |  "type": "customer.created"
      |}""".stripMargin
  val emptyObject = new Gson().fromJson(emptyObjectJson, classOf[Event])
  val eventJson =
    """{
      |  "account": null,
      |  "api_version": "2022-08-01",
      |  "created": 1665320999,
      |  "data": {
      |    "previous_attributes": null,
      |    "object": {
      |      "id": "cus_Ma9ZLkGIc989vk",
      |      "object": "customer",
      |      "address": null,
      |      "balance": 0,
      |      "created": 1665320999,
      |      "currency": null,
      |      "default_source": "card_1LqzGVA7E0whzXsQk82hBxRy",
      |      "delinquent": false,
      |      "description": "(created by Stripe CLI)",
      |      "discount": null,
      |      "email": null,
      |      "invoice_prefix": "DA60E3B0",
      |      "invoice_settings": {
      |        "custom_fields": null,
      |        "default_payment_method": null,
      |        "footer": null,
      |        "rendering_options": null
      |      },
      |      "livemode": false,
      |      "metadata": {},
      |      "name": null,
      |      "phone": null,
      |      "preferred_locales": [],
      |      "shipping": null,
      |      "tax_exempt": "none",
      |      "test_clock": null
      |    }
      |  },
      |  "id": "evt_1LqzGWA7E0whzXsQe1rk1n2B",
      |  "livemode": false,
      |  "object": "event",
      |  "pending_webhooks": 2,
      |  "request": {
      |    "id": "req_qnFe3HlWNrA4dt",
      |    "idempotency_key": "90322d71-d198-4485-a274-196c0fed4c25"
      |  },
      |  "type": "customer.created"
      |}""".stripMargin
  val event = new Gson().fromJson(eventJson, classOf[Event])

  "Stripe route" should "store a correct stripe event" in {
    val recordMetadata =
      new RecordMetadata(new TopicPartition("test_topic", 0), 0L, 0, 0L, 0, 0)

    when(
      stripeProducerMock
        .produce(any(), any(), any())
    ).thenReturn(Future.successful(result = recordMetadata))

    when(stripeServiceMock.handleStripeBody(any(), any()))
      .thenReturn(Future.successful(event))
    Post(s"/stripe_webhooks", eventJson) ~> addHeader(
      "Stripe-Signature",
      "testSignature"
    ) ~> stripeRoute.routes ~> check {

      status shouldBe OK
      contentType shouldBe `application/json`
      val responseText = responseAs[String]
      verify(stripeProducerMock, times(1))
        .produce(event.getType, event.getId, event.toJson)
      verify(stripeServiceMock, times(1))
        .handleStripeBody("testSignature", eventJson)
      responseText shouldEqual s""""${recordMetadata.toString}""""
    }

  }
  it should "return an error when it couldn't deserialize a stripe event" in {

    when(stripeServiceMock.handleStripeBody(any(), any()))
      .thenReturn(Future.successful(emptyObject))
    Post(s"/stripe_webhooks", emptyObjectJson) ~> addHeader(
      "Stripe-Signature",
      "testSignature"
    ) ~> stripeRoute.routes ~> check {

      responseAs[String] shouldEqual s""""Encountered error while trying to retrieve and produce the object of the event: null""""
      status shouldBe InternalServerError
      contentType shouldBe `application/json`
    }
  }

  it should "return an error and push the data to the error topic when stripe event fails verification" in {
    val exception: SignatureVerificationException =
      new SignatureVerificationException(
        "Failed to verify event",
        "TestSignature"
      )
    when(stripeServiceMock.handleStripeBody(any(), any()))
      .thenReturn(Future.failed(exception))
    Post(s"/stripe_webhooks", eventJson) ~> addHeader(
      "Stripe-Signature",
      "testSignature"
    ) ~> stripeRoute.routes ~> check {

      responseAs[String] shouldEqual s""""Failed to verify event""""
      verify(stripeProducerMock, times(1))
        .produce("failed_events", exception.getRequestId, eventJson)
      status shouldBe BadRequest
      contentType shouldBe `application/json`
    }
  }
}
