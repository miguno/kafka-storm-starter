package com.miguno.kafkastorm.storm

import java.util

import backtype.storm.task.TopologyContext
import backtype.storm.topology.base.BaseBasicBolt
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import backtype.storm.tuple.Tuple
import com.miguno.kafkastorm.kafka.{KafkaProducerApp, KafkaProducerAppFactory}
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.{Logger, LoggerFactory}

/**
 * A Storm->Kafka writer bolt.
 *
 * This bolt expects Avro pojos of type `T` as incoming data.  It will Avro-encode these pojos into a binary
 * representation (bytes) according to the Avro schema of `T`, and then send these bytes to Kafka.
 *
 * @param producerFactory A factory to instantiate the required Kafka producer.  We require such a factory because of
 *                        unit testing and the way Storm code is (shipped and) executed in a Storm cluster.  Because
 *                        a bolt is instantiated on a different JVM we cannot simply pass the "final" Kafka producer
 *                        directly to the bolt when we wire the topology.  Instead we must enable each bolt instance to
 *                        create its own Kafka producer when it is starting up (and this startup typically happens in a
 *                        different JVM on a different machine).
 * @param inputField The name of the field in the input tuple to read from.  (Default: "pojo")
 * @tparam T The type of the Avro record (e.g. a `Tweet`) based on the underlying Avro schema being used.  Must be
 *           a subclass of Avro's `SpecificRecordBase`.
 */
class AvroKafkaSinkBolt[T <: SpecificRecordBase : Manifest](
                                                             producerFactory: KafkaProducerAppFactory,
                                                             inputField: String = "pojo")
  extends BaseBasicBolt {

  // Note: Ideally we would like to use TypeTag's instead of Manifest's here.  Doing so would only require replacing
  // `manifest[T]` with `typeOf[T]`, and adding AvroKafkaSinkBolt[T : TypeTag].  Unfortunately there is a known
  // serialization bug in Scala's TypeTag implementation that will trigger runtime exceptions when submitting/running
  // this class in a Storm topology.
  //
  // See "SI-5919: Type tags (and Exprs as well) should be serializable" (https://issues.scala-lang.org/browse/SI-5919)
  val tpe = manifest[T]

  // Must be transient because Logger is not serializable
  @transient lazy private val log: Logger = LoggerFactory.getLogger(classOf[AvroKafkaSinkBolt[T]])

  // Must be transient because Injection is not serializable
  @transient lazy implicit private val specificAvroBinaryInjection: Injection[T, Array[Byte]] =
    SpecificAvroCodecs.toBinary[T]

  // Must be transient because KafkaProducerApp is not serializable.  The factory approach to instantiate a Kafka producer
  // unfortunately means we must use a var combined with `prepare()` -- a val would cause a NullPointerException at
  // runtime for the producer.
  @transient private var producer: KafkaProducerApp = _

  override def prepare(stormConf: util.Map[_, _], context: TopologyContext) {
    producer = producerFactory.newInstance()
  }

  override def execute(tuple: Tuple, collector: BasicOutputCollector) {
    tuple.getValueByField(inputField) match {
      case pojo: T =>
        val bytes = Injection[T, Array[Byte]](pojo)
        log.debug("Encoded pojo " + pojo + " to Avro binary format")
        producer.send(bytes)
      case _ => log.error("Could not decode binary data")
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer) {}

}

object AvroKafkaSinkBolt {

  /**
   * Factory method for Java interoperability.
   *
   * @example {{{
   * // Java example
   * AvroKafkaSinkBolt kafkaSinkBolt = AvroKafkaSinkBolt.ofType(Tweet.class)(brokerList, ...);
   * }}}
   *
   * @param cls
   * @tparam T
   * @return
   */
  def ofType[T <: SpecificRecordBase](cls: java.lang.Class[T])(
    producerFactory: KafkaProducerAppFactory,
    inputFieldName: String = "pojo") = {
    val manifest = Manifest.classType[T](cls)
    newInstance[T](producerFactory, inputFieldName)(manifest)
  }

  private def newInstance[T <: SpecificRecordBase](
                                                    producerFactory: KafkaProducerAppFactory,
                                                    inputFieldName: String = "pojo")
                                                  (implicit man: Manifest[T]) =
    new AvroKafkaSinkBolt[T](producerFactory, inputFieldName)

}