package edu.stanford.cs248.project

import java.nio._
import javax.imageio.ImageIO

import scala.math._

import org.lwjgl.opengl._

import edu.stanford.cs248.project.entity._
import edu.stanford.cs248.project.opengl._
import edu.stanford.cs248.project.util._

class LevelModel(val name: String)
  extends TexturedVBOModel(
    new ImageTexture("/textures/terrain_d.png"),
    new ImageTexture("/textures/terrain_s.png"))
{
  val heightMap = new ImageMapGrayscale("/levels/"+name+"_h.png", 500)
  val deltaXMap = heightMap.deltaXMap
  val deltaYMap = heightMap.deltaYMap

  val xSize = heightMap.xSize
  val ySize = heightMap.ySize

  val zScale = 0.03f
  val SIZE = 0.50f

  var indexCount = 0
  var newEntities: List[Entity] = List()

  val texMap = new ImageMapObjMap("/levels/"+name+"_t.png",
    Map(
      0x000000->0,
      0x0000ff->1,
      0x00ff00->2,
      0x00ffff->3,
      0xff0000->4,
      //0xff00ff->5,
      //0xffff00->6,
      0xffffff->7,
      0x202020->5,//8, //Crystal
      0x000050->6//9  //Block
    )
  )

  def inBounds(x: Double, y: Double) =
    x > -SIZE && y > -SIZE && x < xSize - SIZE && y < ySize - SIZE

  def height(x: Double, y: Double) = {
    val clampedX = round(max(min(xSize-1.001+SIZE, x), -SIZE))
    val clampedY = round(max(min(ySize-1.001+SIZE, y), -SIZE))
    heightMap.valueAt(clampedX.toInt, clampedY.toInt)*zScale
  }

  for (x <- 0 until xSize; y <- 0 until ySize) {
    val color = texMap.valueAt(x, y)
    val height = heightMap.valueAt(x, y) * zScale

    // Special case colors. Create new entities and append to list.
    if (color == 0) {

    } else if (color == 5) {
      newEntities = (new Crystal(x, y, height)) :: newEntities
    } else if (color == 6) {
      newEntities = (new Block(x, y, height)) :: newEntities
    }
  }

  def getVertices() = {
    // insert one vertex per pixel in the heightmap
    // note in this case, the top-left of the image corresponds to (0,0)
    // and x+ and y+ are right and down in the image
    // This is different from opengl's texture coordinate system
    val floorCorners = Array(
      (-SIZE, -SIZE), (SIZE, -SIZE), (SIZE, SIZE), (-SIZE, SIZE))

    var vertVec = new scala.collection.immutable.VectorBuilder[Vertex]()

    // get texture "s" coord
    val texSUnit = 1.0f/8.0f
    val texTUnit = 1.0f/3.0f

    def drawXVertTile(xf: Float, yf: Float,
                      tileZTop: Float, tileZHeight: Float, texT0Units: Float,
                      nx: Float, texS0: Float) =
    {
      vertVec ++= floorCorners.map { case(dy, dz) =>
        Vertex(
          xf+SIZE, yf+dy*nx, tileZTop+(dz-SIZE)*tileZHeight,
          nx, 0, 0,
          texS0+(dy+SIZE)*texSUnit,
          (texT0Units+(1.0f-tileZHeight)+(dz+SIZE)*tileZHeight)*texTUnit)
      }
    }

    def drawYVertTile(xf: Float, yf: Float,
                      tileZTop: Float, tileZHeight: Float, texT0Units: Float,
                      ny: Float, texS0: Float) =
    {
      vertVec ++= floorCorners.map { case(dx, dz) =>
        Vertex(
          xf-dx*ny, yf+SIZE, tileZTop+(dz-SIZE)*tileZHeight,
          0, ny, 0,
          texS0+(dx+SIZE)*texSUnit,
          (texT0Units+(1.0f-tileZHeight)+(dz+SIZE)*tileZHeight)*texTUnit)
      }
    }

    // sub 'x' or 'y' for 'u'
    def commonValues(zf: Float, dzdu: Float) = {
      val nu = if(dzdu > 0) -1.0f else 1.0f
      val zBot = min(zf, zf+dzdu)
      val zHeight = abs(dzdu)

      // drop "top" tile
      val topTileHeight = min(1.0f, zHeight)
      val topTileZTop   = zBot+zHeight

      (nu, zBot, zHeight, topTileHeight, topTileZTop)
    }

    def drawXWall(xf: Float, yf: Float, zf: Float, dzdx: Float, texS0: Float) =
    {
      val (nx, zBot, zHeight, topTileHeight, topTileZTop) =
        commonValues(zf, dzdx)

      // draw the top vertical tile
      drawXVertTile(xf, yf, topTileZTop, topTileHeight, 1, nx, texS0)

      // draw rest of the vertical tiles
      if(zHeight > 1.0) {
        for(tileTop <-
              Range.Double(zBot+zHeight-1, zBot-1, -1).map(_.toFloat))
        {
          drawXVertTile(xf, yf, tileTop, min(1.0f, tileTop-zBot), 0, nx, texS0)
        }
      }
    }

    def drawYWall(xf: Float, yf: Float, zf: Float, dzdy: Float, texS0: Float) =
    {
      val (ny, zBot, zHeight, topTileHeight, topTileZTop) =
        commonValues(zf, dzdy)

      // draw the top vertical tile
      drawYVertTile(xf, yf, topTileZTop, topTileHeight, 1, ny, texS0)

      // draw rest of the vertical tiles
      if(zHeight > 1.0) {
        for(tileTop <-
              Range.Double(zBot+zHeight-1, zBot-1, -1).map(_.toFloat))
        {
          drawYVertTile(xf, yf, tileTop, min(1.0f, tileTop-zBot), 0, ny, texS0)
        }
      }
    }

    for(y <- 0 until ySize; x <- 0 until xSize) {
      val xf = x.toFloat
      val yf = y.toFloat
      val zf = heightMap.valueAt(x,y)*zScale
      val dzdx = deltaXMap.valueAt(x,y)*zScale
      val dzdy = deltaYMap.valueAt(x,y)*zScale

      val hc = heightMap.valueAt(x,y).asInstanceOf[Byte]

      // paint floor tiles
      val floorS0 = texSUnit*texMap.valueAt(x, y)

      vertVec ++= floorCorners.map { case(dx, dy) =>
        Vertex(
          xf+dx, yf+dy, zf,
          0, 0, 1,
          // The "- dx/50.0f" and "- dy/50.0f" nudges the texture coordinates just
          // slightly inwards, so that little white dots don't appear.
          floorS0+(dx+SIZE)*texSUnit - dx/(SIZE * 100), (2+dy+SIZE)*texTUnit - dy/(SIZE * 100))
      }

      // paint x facing walls
      if(dzdx != 0) {
        // use texture of "higher" tile"
        val texS0 = texSUnit*texMap.valueAt(if(dzdx < 0) x else x+1, y)
        drawXWall(xf, yf, zf, dzdx, texS0)
      }

      if(dzdy != 0) {
        // use texture of "higher" tile"
        val texS0 = texSUnit*texMap.valueAt(x, if(dzdy < 0) y else y+1)
        drawYWall(xf, yf, zf, dzdy, texS0)
      }

      // draw 'edge' walls
      if(x == 0 && zf > 0.0) {
        drawXWall(-1.0f, yf, 0.0f, zf, texSUnit*texMap.valueAt(x, y))
      }

      if(x == xSize-1 && zf > 0.0) {
        drawXWall(xf, yf, zf, -zf, texSUnit*texMap.valueAt(x, y))
      }

      if(y == 0 && zf > 0.0) {
        drawYWall(xf, -1.0f, 0.0f, zf, texSUnit*texMap.valueAt(x, y))
      }

      if(y == ySize-1 && zf > 0.0) {
        drawYWall(xf, yf, zf, -zf, texSUnit*texMap.valueAt(x, y))
      }
    }

    vertVec.result()
  }

  def getIndices() = (0 until nVerts)
}

class Level(val name: String) extends VBOModelEntity {
  val model = new LevelModel(name)
  var newEntities: List[Entity] = model.newEntities

  // origin of the model in WORLD SPACE
  x = 0f
  y = 0f
  z = 0f

  def height(x: Double, y: Double) = model.height(x, y)
  def inBounds(x: Double, y: Double) = model.inBounds(x, y)
  def zScale = model.zScale

  def intersect(x: Float, y: Float, z: Float): Option[Tuple2[Int, Int]] = {
    for (loc_x <- 0 until model.xSize; loc_y <- 0 until model.ySize) {
      val loc_z = model.height(loc_x, loc_y)

      if (x > loc_x - model.SIZE && x < loc_x + model.SIZE &&
          y > loc_y - model.SIZE && y < loc_y + model.SIZE &&
          z < loc_z && z > loc_z - 2 * model.SIZE) {
        return Some((loc_x, loc_y))
      }
    }

    None
  }
  override def traits() = List("level", "render", "update")

  def setLights() = {
    import GL11._

    // set one directional light
    val lightId = GL_LIGHT0

    glEnable(GL_LIGHTING);

    val buf = ByteBuffer
      .allocateDirect(16).order(ByteOrder.nativeOrder()).asFloatBuffer()

    buf.put(Array(0.7f, 0.7f, 0.7f, 1.0f)).flip()
    glLight(lightId, GL_AMBIENT,  buf)
    buf.put(Array(0.7f, 0.7f, 0.7f, 1.0f)).flip()
    glLight(lightId, GL_DIFFUSE,  buf)
    /*buf.put(Array(1.0f, 1.0f, 1.0f, 1.0f)).flip()
    glLight(lightId, GL_SPECULAR, buf)*/
    buf.put(Array(20.0f, 20.0f, 400.0f*zScale, 1.0f)).flip()
    glLight(lightId, GL_POSITION, buf)
  }
}
