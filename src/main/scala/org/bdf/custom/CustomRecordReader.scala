package org.bdf.custom

import java.time.LocalDateTime
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.{FileSplit, TextInputFormat}
import org.apache.hadoop.mapreduce.{InputSplit, RecordReader, TaskAttemptContext}
import org.apache.hadoop.fs.{FSDataInputStream, FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.task.TaskAttemptContextImpl
import org.apache.hadoop.io.DataOutputBuffer

import java.io.IOException
import scala.collection.mutable.MutableList



class CustomInputFormat extends FileInputFormat[LongWritable, Text] {

  override def createRecordReader(split: InputSplit, context: TaskAttemptContext): RecordReader[LongWritable, Text] = {
    new CustomRecordReader
  }

}

class CustomRecordReader extends RecordReader[LongWritable, Text] {
  private var end: Long = _
  private var stillInChunk = true
  private val key = new LongWritable()
  private val value = new Text()
  private var fsin: FSDataInputStream = _
  private val buffer = new DataOutputBuffer()
  private var keySet = scala.collection.mutable.Set[Long]()
  private val endTag = "\\.".getBytes()

  override def initialize(inputSplit: InputSplit, taskAttemptContext: TaskAttemptContext): Unit = {
    val split = inputSplit.asInstanceOf[FileSplit]
    val conf = taskAttemptContext.getConfiguration
    val path = split.getPath
    val fs = path.getFileSystem(conf)
    println(s"[Custom log] ${LocalDateTime.now() }    Reader Initialize")
    println(s"[Custom log] ${LocalDateTime.now() }   FileSplit: $inputSplit")
    println(s"[Custom log] ${LocalDateTime.now() }    File path: $path")
    fsin = fs.open(path)
    val start = split.getStart
    println(s"[Custom log] ${LocalDateTime.now()}    file start: $start")
    end = split.getStart + split.getLength
    println(s"[Custom log] ${LocalDateTime.now()}    file end: $end")
    fsin.seek(start)
    if (start != 0) {
      readUntilMatch(endTag, false)
    }
  }

  override def nextKeyValue(): Boolean = {
    println(s"[Custom log] ${LocalDateTime.now() }   Inside nextKeyValue method")
    if (!stillInChunk) {
      println(s"[Custom log] ${LocalDateTime.now() }    stillInChunk=False")
      return false
  }
    val status = readUntilMatch(endTag, true)
    println(s"[Custom log] ${LocalDateTime.now() }    status: $status")
    if (keySet.contains(fsin.getPos)) println(s"Trying to add an already added key: $key")
    else
    {
      value.set(buffer.getData, 0, buffer.getLength)
      println(s"[Custom log] ${LocalDateTime.now() }    setting key: ${fsin.getPos} with value length = ${buffer.getLength}")
      key.set(fsin.getPos)
      keySet+=fsin.getPos 
    }
    buffer.reset()
    if (!status) {
      stillInChunk = false
    }
    true
  }

  override def getCurrentKey: LongWritable = key

  override def getCurrentValue: Text = value

  override def getProgress: Float = 0

  override def close(): Unit = fsin.close()

  private def readUntilMatch(matcher: Array[Byte], withinBlock: Boolean): Boolean = {
    println(s"[Custom log] ${LocalDateTime.now() }  Inside readUntilMatch method")
    var i = 0
    while (true) {
      val b = fsin.read()
      if (b == -1) return false
      if (withinBlock) buffer.write(b)
      if (b == matcher(i)) {
        i += 1
        if (i >= matcher.length) {
          println(s"[Custom log] ${LocalDateTime.now() }     Reached b: $b")
          println(s"[Custom log] ${LocalDateTime.now() }    found pos: ${fsin.getPos}")
          return fsin.getPos < end
        }
      } else i = 0
    }
    println(s"[Custom log] ${LocalDateTime.now() }    returned False inside readUntilMatch")
    false
  }
}


object Main {
  def main(args: Array[String]): Unit = {
    println("Hello!")
    val conf = new Configuration()
    val path = new Path("file:////workspaces/spark-templates/src/main/scala/org/bdf/custom/text.txt")
    val reader = new CustomRecordReader()
    val fileSplit = new FileSplit(path, 0, 400, Array.empty)
    println(s"[Custom log] ${LocalDateTime.now() }     fileSplit: $fileSplit")
    val attemptId = new TaskAttemptID(new TaskID(new JobID(), TaskType.MAP, 0), 0)
    val hadoopAttemptContext = new TaskAttemptContextImpl(conf, attemptId)
    reader.initialize(fileSplit, hadoopAttemptContext)
    while (reader.nextKeyValue()) {
      val key = reader.getCurrentKey
      val value = reader.getCurrentValue
      println(s"[Custom log] ${LocalDateTime.now() }    $key: $value")
    }
    reader.close()
  }
}

