package geotrellis.spark.io.cassandra.spatial

import com.datastax.driver.core.DataType.{blob, cint, text}
import com.datastax.driver.core.schemabuilder.SchemaBuilder
import com.datastax.spark.connector._
import geotrellis.raster.Tile
import geotrellis.spark._
import geotrellis.spark.io.avro.KeyCodecs._
import geotrellis.spark.io.avro.{AvroEncoder, TupleCodec}
import geotrellis.spark.io.cassandra._
import geotrellis.spark.io.index._
import geotrellis.spark.utils._

object SpatialRasterRDDWriter extends RasterRDDWriter[SpatialKey] {

  def createTableIfNotExists(tileTable: String)(implicit session: CassandraSession): Unit = {
    val schema = SchemaBuilder.createTable(session.keySpace, tileTable).ifNotExists()
      .addPartitionKey("reverse_index", text)
      .addClusteringColumn("zoom", cint)
      .addClusteringColumn("indexer", text)
      .addClusteringColumn("name", text)
      .addColumn("value", blob)

    session.execute(schema)
  }

  def saveRasterRDD(
    layerId: LayerId,
    raster: RasterRDD[SpatialKey], 
    kIndex: KeyIndex[SpatialKey],
    tileTable: String)(implicit session: CassandraSession): Unit = {

    lazy val writeCodec = KryoWrapper(TupleCodec[SpatialKey, Tile])
    val closureKeyIndex = kIndex

    raster
      .map(
        KryoClosure {
          case (key, tile) =>

            val value = AvroEncoder.toBinary(key, tile)(writeCodec.value)
            val indexer = closureKeyIndex.toIndex(key).toString

            (indexer.reverse, layerId.zoom, indexer, layerId.name, value)
        }
      )
      .saveToCassandra(session.keySpace, tileTable, SomeColumns("reverse_index", "zoom", "indexer", "name", "value"))
  }
}
