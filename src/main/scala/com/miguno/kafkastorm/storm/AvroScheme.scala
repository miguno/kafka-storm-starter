package com.miguno.kafkastorm.storm

import backtype.storm.spout.Scheme
import backtype.storm.tuple.{Fields, Values}
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import org.apache.avro.specific.SpecificRecordBase

import scala.util.{Failure, Success}

/**
 * A custom binaryAvro->pojoAvro `backtype.storm.spout.Scheme` to auto-deserialize a spout's incoming data.  You can
 * parameterize this scheme with the Avro type `T` of the spout's expected input data.
 *
 * In the case of `storm.kafka.KafkaSpout` its default scheme is Storm's `backtype.storm.spout.RawMultiScheme`,
 * which simply returns the raw bytes of the incoming data (i.e. leaving deserialization up to you in subsequent bolts
 * such as [[AvroDecoderBolt]]).  Alternatively, you configure the spout to use this custom scheme.  If you do, then the
 * spout will automatically deserialize its incoming data into pojos.  Note that you will need to register a custom
 * Kryo decorator for the Avro type `T`, see [[TweetAvroKryoDecorator]] for an example.
 *
 * @example {{{
 * import backtype.storm.spout.SchemeAsMultiScheme
 * import com.miguno.avro.Tweet
 * storm.kafka.{KafkaSpout, SpoutConfig}
 *
 * val spoutConfig = new SpoutConfig(...)
 * spoutConfig.scheme = new SchemeAsMultiScheme(new AvroScheme[Tweet])
 * val kafkaSpout = new KafkaSpout(spoutConfig)
 * }}}
 *
 * @tparam T The type of the Avro record (e.g. a `Tweet`) based on the underlying Avro schema being used.  Must be
 *           a subclass of Avro's `SpecificRecordBase`.
 */
class AvroScheme[T <: SpecificRecordBase : Manifest] extends Scheme {

  // Note: Ideally we would like to use TypeTag's instead of Manifest's here.  Doing so would only require replacing
  // `manifest[T]` with `typeOf[T]`, and adding AvroScheme[T : TypeTag].  Unfortunately there is a known serialization
  // bug in the TypeTag implementation of Scala versions <= 2.11.1 that will trigger runtime exceptions when
  // submitting/running this class in a Storm topology.
  //
  // See "SI-5919: Type tags (and Exprs as well) should be serializable" (https://issues.scala-lang.org/browse/SI-5919)
  val tpe = manifest[T]

  private val OutputFieldName = "pojo"

  @transient lazy implicit private val specificAvroBinaryInjection = SpecificAvroCodecs.toBinary[T]

  override def deserialize(bytes: Array[Byte]): java.util.List[AnyRef] = {
    val result = Injection.invert(bytes)
    result match {
      case Success(pojo) => new Values(pojo)
      case Failure(e) => throw new RuntimeException("Could not decode input bytes")
    }
  }

  override def getOutputFields() = new Fields(OutputFieldName)

}

object AvroScheme {

  /**
   * Factory method for Java interoperability.
   *
   * @example {{{
   * // in Java
   * AvroScheme avroScheme = AvroScheme.ofType(Tweet.class);
   * }}}
   *
   * @param cls
   * @tparam T
   * @return
   */
  def ofType[T <: SpecificRecordBase](cls: java.lang.Class[T]) = {
    val manifest = Manifest.classType[T](cls)
    newInstance[T](manifest)
  }

  private def newInstance[T <: SpecificRecordBase : Manifest] = new AvroScheme[T]

}