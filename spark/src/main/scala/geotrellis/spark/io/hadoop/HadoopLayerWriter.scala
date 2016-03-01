package geotrellis.spark.io.hadoop

import geotrellis.raster.{MultiBandTile, Tile}
import geotrellis.spark.io.json._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.index.{KeyIndexMethod, KeyIndex}

import org.apache.avro.Schema
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.reflect._

class HadoopLayerWriter(
  rootPath: Path,
  val attributeStore: AttributeStore[JsonFormat]
)(implicit sc: SparkContext) extends LayerWriter[LayerId] {

  def write[
    K: AvroRecordCodec: JsonFormat: KeyIndexJsonFormat: ClassTag,
    V: AvroRecordCodec: ClassTag,
    M: JsonFormat
  ](id: LayerId, rdd: RDD[(K, V)] with Metadata[M], keyIndex: KeyIndex[K], keyBounds: KeyBounds[K]): Unit = {
    val layerPath = new Path(rootPath,  s"${id.name}/${id.zoom}")

    val header =
      HadoopLayerHeader(
        keyClass = classTag[K].toString(),
        valueClass = classTag[V].toString(),
        path = layerPath
      )
    val metaData = rdd.metadata

    try {
      attributeStore.writeLayerAttributes(id, header, metaData, keyBounds, keyIndex, KeyValueRecordCodec[K, V].schema)
      HadoopRDDWriter.write[K, V](rdd, layerPath, keyIndex)
    } catch {
      case e: Exception => throw new LayerWriteError(id).initCause(e)
    }
  }
}

object HadoopLayerWriter {
  def apply(rootPath: Path, attributeStore: AttributeStore[JsonFormat])(implicit sc: SparkContext): HadoopLayerWriter =
    new HadoopLayerWriter(
      rootPath = rootPath,
      attributeStore = attributeStore
    )

  def apply(rootPath: Path)(implicit sc: SparkContext): HadoopLayerWriter =
    apply(
      rootPath = rootPath,
      attributeStore = HadoopAttributeStore.default(rootPath)
    )
}
