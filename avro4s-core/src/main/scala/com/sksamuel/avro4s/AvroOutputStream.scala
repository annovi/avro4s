package com.sksamuel.avro4s

import java.io.{File, OutputStream}
import java.nio.file.{Files, Path}

import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory

trait AvroOutputStream[T] {
  def close(): Unit
  def flush(): Unit
  def fSync(): Unit
  def write(t: T): Unit
  def write(ts: Seq[T]): Unit = ts.foreach(write)
}

class AvroBinaryOutputStream[T](os: OutputStream)(implicit schemaFor: SchemaFor[T], toRecord: ToRecord[T])
  extends AvroOutputStream[T] {

  val dataWriter = new GenericDatumWriter[GenericRecord](schemaFor())
  val encoder = EncoderFactory.get().binaryEncoder(os, null)

  override def close(): Unit = {
    encoder.flush()
    os.close()
  }

  override def write(t: T): Unit = dataWriter.write(toRecord(t), encoder)
  override def flush(): Unit = encoder.flush()
  override def fSync(): Unit = ()
}

class AvroDataOutputStream[T](os: OutputStream)(implicit schemaFor: SchemaFor[T], toRecord: ToRecord[T])
  extends AvroOutputStream[T] {

  val datumWriter = new GenericDatumWriter[GenericRecord](schemaFor())
  val dataFileWriter = new DataFileWriter[GenericRecord](datumWriter)
  dataFileWriter.create(schemaFor(), os)

  override def close(): Unit = {
    dataFileWriter.close()
    os.close()
  }

  override def write(t: T): Unit = dataFileWriter.append(toRecord(t))
  override def flush(): Unit = dataFileWriter.flush()
  override def fSync(): Unit = dataFileWriter.fSync()
}

object AvroOutputStream {
  def apply[T: SchemaFor : ToRecord](file: File): AvroOutputStream[T] = apply(file.toPath, true)
  def apply[T: SchemaFor : ToRecord](file: File, binaryModeDisabled: Boolean): AvroOutputStream[T] = apply(file.toPath, binaryModeDisabled)

  def apply[T: SchemaFor : ToRecord](path: Path): AvroOutputStream[T] = apply(Files.newOutputStream(path), true)
  def apply[T: SchemaFor : ToRecord](path: Path, binaryModeDisabled: Boolean): AvroOutputStream[T] = apply(Files.newOutputStream(path), binaryModeDisabled)

  def apply[T: SchemaFor : ToRecord](os: OutputStream): AvroOutputStream[T] = apply(os, false)
  def apply[T: SchemaFor : ToRecord](os: OutputStream, binaryModeDisabled: Boolean): AvroOutputStream[T] = {
    if (binaryModeDisabled) new AvroDataOutputStream[T](os)
    else new AvroBinaryOutputStream[T](os)
  }
}