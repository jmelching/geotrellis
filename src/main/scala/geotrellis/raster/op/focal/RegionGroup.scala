package geotrellis.raster.op.focal

import geotrellis._

import spire.syntax._

import scala.collection.mutable

case class RegionGroup(r:Op[Raster]) extends Op1(r)({
  r =>
    var regionId = -1
    val regions = new RegionPartition()
    val cols = r.cols
    val rows = r.rows
    val data = IntArrayRasterData.empty(cols,rows)
    cfor(0)(_ < rows, _ + 1) { row =>
      var valueToLeft = NODATA
      cfor(0)(_ < cols, _ + 1) { col =>
        val v = r.get(col,row)
        if(v != NODATA) {
          val top =
            if(row > 0) { r.get(col,row - 1) }
            else { v + 1 }

          if(v == top) {
            val topRegion = data.get(col,row-1)
            if(v == valueToLeft) {
              regions.add(topRegion,data.get(col-1,row))
              data.set(col,row,topRegion)
            } else {
              data.set(col,row,topRegion)
            }
          } else {
            if(v == valueToLeft) {
              data.set(col,row,data.get(col-1,row))
            } else {
              // New region
              regionId += 1
              regions.add(regionId)
              data.set(col,row,regionId)
            }
          }
        }
        valueToLeft = v
      }
    }

    // Set equivilant regions to minimum
    cfor(0)(_ < rows, _ + 1) { row =>
      cfor(0)(_ < cols, _ + 1) { col =>
        val v = data.get(col,row)
        if(v != NODATA) { data.set(col,row,regions.getClass(v)) }
      }
    }

    Result(Raster(data,r.rasterExtent))
})

class RegionPartition {
  private val regionMap = mutable.Map[Int,Int]()
  private val minMap = mutable.ListBuffer[Int]()

  private var maxRegionIndex = -1
  def regionCount:Int = maxRegionIndex + 1

  def add(x:Int) =
    if(!regionMap.contains(x)) {
      maxRegionIndex += 1
      regionMap(x) = maxRegionIndex
      minMap += x
    }

  def add(x:Int,y:Int):Unit = {
    if(!regionMap.contains(x)) {
      if(!regionMap.contains(y)) {
        // x and y are not mapped
        maxRegionIndex += 1
        regionMap(x) = maxRegionIndex
        regionMap(y) = maxRegionIndex
        minMap += math.min(x,y)
      } else {
        // x is not mapped, y is mapped
        val r = regionMap(y)
        regionMap(x) = r
        val my = minMap(r)
        if(x == math.min(x,my)) { 
          minMap(r) = x
        }
      }
    } else {
      if(!regionMap.contains(y)) {
        // x is mapped, y is not mapped
        val r = regionMap(x)
        regionMap(y) = r
        val mx = minMap(r)
        if(y == math.min(mx,y)) {
          minMap(r) = y
        }
      } else {
        // both x and y are mapped
        val rx = regionMap(x)
        val ry = regionMap(y)
        val mx = minMap(rx)
        val my = minMap(ry)
        if(my == math.min(mx,my)) {
          regionMap(x) = ry
          minMap(rx) = my
        } else {
          regionMap(y) = rx
          minMap(ry) = mx
        }
      }
    }
  }

  def getClass(x:Int) = 
    if(!regionMap.contains(x)) { sys.error(s"There is no partition containing $x") }
    else { 
      minMap(regionMap(x)) 
    }
}
