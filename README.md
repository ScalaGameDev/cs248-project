# cs248 final project

A game written in Scala.

## Development notes

### Pixel position reconstruction

We originally used the z-buffer built during the g-buffer construction to sample pixel depths. We would then get to the pixel's view space by using the inverse projection matrix.

However, we discovered that the z-buffer uses the vast majority of its precision in the area very close to the 'eye'. This leads to pretty poor precision z-buffers.

## Thanks to

* `jPCT` - www.jpct.net
