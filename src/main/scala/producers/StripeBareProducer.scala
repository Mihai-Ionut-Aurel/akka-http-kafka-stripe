package producers

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.Serializer

import scala.concurrent.{ExecutionContext, Future}

class StripeBareProducer[K, V](logger: LoggingAdapter,
                               bootstrapServers: String,
                               keySerializer: Serializer[K],
                               valueSerializer: Serializer[V],
)(implicit val actorSystem: ActorSystem, executionContext: ExecutionContext) {

  private val kafkaProducerSettings: ProducerSettings[K, V] =
    ProducerSettings(actorSystem, keySerializer, valueSerializer)
      .withBootstrapServers(bootstrapServers)

  private val producer: SendProducer[K, V] = SendProducer(kafkaProducerSettings)

  def produce(topic: String, key: K, value: V): Future[RecordMetadata] = {

    val data: ProducerRecord[K, V] =
      new ProducerRecord(topic, key, value)

    logger.info(s"Produced record with id ${key.toString}")
    producer.send(data)
  }
}
