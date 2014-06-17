package com.miguno.kafkastorm.storm

import backtype.storm.topology.base.BaseBasicBolt
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import backtype.storm.tuple.{Fields, Tuple, Values}
import com.google.common.base.Throwables
import com.twitter.bijection.avro.SpecificAvroCodecs
import com.twitter.bijection.Injection
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.{Logger, LoggerFactory}
import scala.util.{Try, Failure, Success}

/**
 * An binaryAvro->pojoAvro converter bolt.
 *
 * This bolt expects incoming data in Avro-encoded binary format, serialized according to the Avro schema of `T`.  It
 * will deserialize the incoming data into a `T` pojo, and emit this pojo to downstream consumers.  As such this bolt
 * can be considered the Storm equivalent of Twitter Bijection's `Injection.invert[T, Array[Byte]](bytes)` for
 * Avro data.
 *
 * By using this bolt you don't need to write another decoder bolt just because the bolt needs to handle a different
 * Avro schema.
 *
 * @example {{{
 * import backtype.storm.topology.TopologyBuilder
 * import com.miguno.avro.Tweet
 *
 * val builder = new TopologyBuilder
 * // ...spout is set up here...
 * val decoderBolt = new AvroDecoderBolt[Tweet]
 * builder.setBolt(decoderBoltId, decoderBolt).shuffleGrouping(spoutId) // or whatever grouping you need
 * }}}
 *
 * @param inputField The name of the field in the input tuple to read from.  (Default: "bytes")
 * @param outputField The name of the field in the output tuple to write to.  (Default: "pojo")
 * @tparam T The type of the Avro record (e.g. a `Tweet`) based on the underlying Avro schema being used.  Must be
 *           a subclass of Avro's `SpecificRecordBase`.
 */
class AvroDecoderBolt[T <: SpecificRecordBase : Manifest](
  inputField: String = "bytes",
  outputField: String = "pojo")
  extends BaseBasicBolt {

  // Note: Ideally we would like to use TypeTag's instead of Manifest's here.  Doing so would only require replacing
  // `manifest[T]` with `typeOf[T]`, and adding AvroDecoderBolt[T : TypeTag].  Unfortunately there is a known
  // serialization bug in Scala's TypeTag implementation that will trigger runtime exceptions when submitting/running
  // this class in a Storm topology.
  //
  // See "SI-5919: Type tags (and Exprs as well) should be serializable" (https://issues.scala-lang.org/browse/SI-5919)
  val tpe = manifest[T]

  // Must be transient because Logger is not serializable
  @transient lazy private val log: Logger = LoggerFactory.getLogger(classOf[AvroDecoderBolt[T]])

  // Must be transient because Injection is not serializable.  Must be implicit because that's who Injection works.
  @transient lazy implicit private val specificAvroBinaryInjection: Injection[T, Array[Byte]] =
    SpecificAvroCodecs.toBinary[T]

  override def execute(tuple: Tuple, collector: BasicOutputCollector) {
    val readTry = Try(tuple.getBinaryByField(inputField))
    readTry match {
      case Success(bytes) if bytes != null => decodeAndSinkToKafka(bytes, collector)
      case Success(_) => log.error("Reading from input tuple returned null")
      case Failure(e) => log.error("Could not read from input tuple: " + Throwables.getStackTraceAsString(e))
    }
  }

  private def decodeAndSinkToKafka(bytes: Array[Byte], collector: BasicOutputCollector) {
    require(bytes != null, "bytes must not be null")
    val decodeTry = Injection.invert[T, Array[Byte]](bytes)
    decodeTry match {
      case Success(pojo) =>
        log.debug("Binary data decoded into pojo: " + pojo)
        collector.emit(new Values(pojo))
        ()
      case Failure(e) => log.error("Could not decode binary data: " + Throwables.getStackTraceAsString(e))
    }
  }

  override def declareOutputFields(declarer: OutputFieldsDeclarer) {
    declarer.declare(new Fields(outputField))
  }

}

object AvroDecoderBolt {

  /**
   * Factory method for Java interoperability.
   *
   * @example {{{
   * // in Java
   * AvroDecoderBolt decoderBolt = AvroDecoderBolt.ofType(Tweet.class);
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

  private def newInstance[T <: SpecificRecordBase : Manifest] = new AvroDecoderBolt[T]

}