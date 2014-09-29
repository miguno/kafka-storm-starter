package com.miguno.kafkastorm.spark.serialization

import com.esotericsoftware.kryo.Kryo
import com.miguno.avro.Tweet
import com.twitter.chill.avro.AvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.spark.serializer.KryoRegistrator

/**
 * We register custom classes with Kryo, see the explanations in the
 * [[http://spark.apache.org/docs/1.1.0/tuning.html#data-serialization Tuning Spark]] guide.
 *
 * "If you don’t register your custom classes, Kryo will still work, but it will have to store the full class name with
 * each object, which is wasteful."
 */
class KafkaSparkStreamingRegistrator extends KryoRegistrator {

  override def registerClasses(kryo: Kryo) {
    // Registers a serializer for any generic Avro records.  The kafka-storm-starter project does not yet include
    // examples that work on generic Avro records, but we keep this registration for the convenience of our readers.
    kryo.register(classOf[GenericRecord], AvroSerializer.GenericRecordSerializer[GenericRecord]())
    // Registers a serializer specifically for the, well, specific Avro record `Tweet`
    kryo.register(classOf[Tweet], AvroSerializer.SpecificRecordSerializer[Tweet])
    ()
  }

}