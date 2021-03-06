package io.hydrosphere.spark_ml_serving.reader

import parquet.io.api.{GroupConverter, RecordMaterializer}
import parquet.schema.MessageType

class SimpleRecordMaterializer(schema: MessageType) extends RecordMaterializer[SimpleRecord] {

  val root: SimpleRecordConverter = new SimpleRecordConverter(schema, null, null)

  override def getRootConverter: GroupConverter = root

  override def getCurrentRecord: SimpleRecord = root.record
}