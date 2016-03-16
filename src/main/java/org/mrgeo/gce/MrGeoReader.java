/*
 *     Copyright 2015 DigitalGlobe, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.mrgeo.gce;

import it.geosolutions.imageio.maskband.DatasetLayout;
import it.geosolutions.imageio.maskband.DefaultDatasetLayoutImpl;
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.*;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.data.image.MrsImageReader;
import org.mrgeo.data.raster.RasterUtils;
import org.mrgeo.image.MrsImage;
import org.mrgeo.image.MrsPyramid;
import org.mrgeo.image.MrsPyramidMetadata;
import org.mrgeo.image.RasterTileMerger;
import org.mrgeo.utils.Bounds;
import org.mrgeo.utils.LongRectangle;
import org.mrgeo.utils.TMSUtils;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.media.jai.ImageLayout;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MrGeoReader extends AbstractGridCoverage2DReader implements GridCoverage2DReader
{
private static final Logger log = Logging.getLogger("org.mrgeo.gce.MrGeoReader");

final static String USERNAME = "user.name";
final static String USER_ROLES = "user.roles";

private Properties config = new Properties();

ProviderProperties providerProperties = ProviderProperties.fromDelimitedString("");

Set<String> layers = new HashSet<>();

CoordinateReferenceSystem epsg4326 = null;

public MrGeoReader(Properties config) throws IOException
{
  this.config = config;

  String epsg = "EPSG:4326";
  try
  {
    epsg4326 = CRS.decode(epsg);
  }
  catch (FactoryException e)
  {
    e.printStackTrace();
  }


  providerProperties = new ProviderProperties(config.getProperty(USERNAME, ""), config.getProperty(USER_ROLES, ""));

  loadLayers();

}

private void loadLayers() throws IOException
{
  Collections.addAll(layers, DataProviderFactory.listImages(providerProperties));

  if (log.isLoggable(Level.FINE))
  {
    log.fine("Layers found:");
    for (String layer : layers)
    {
      log.fine("  " + layer);
    }
  }
}

public Format getFormat()
{
  return new MrGeoFormat(config);
}

@Override
protected boolean checkName(String name)
{
  log.fine("Checking for coverage: " + name  + (layers.contains(name) ? " FOUND" : " NOT FOUND"));

  return layers.contains(name);
}

@Override
public GridCoverage2D read(GeneralParameterValue[] parameters) throws IOException
{
  throw new UnsupportedOperationException("MrGeoReader does not have a single coverage mode!");
}

@Override
public GridCoverage2D read(String name, GeneralParameterValue[] parameters) throws IOException
{
  log.fine("Reading coverage: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  ReferencedEnvelope requestedEnvelope = null;
  Rectangle dim = null;

  if (parameters != null) {
    for (GeneralParameterValue parameter : parameters)
    {
      final ParameterValue param = (ParameterValue) parameter;
      final ReferenceIdentifier riname = param.getDescriptor().getName();
      if (riname.equals(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName()))
      {
        final GridGeometry2D gg = (GridGeometry2D) param.getValue();
        try
        {
          requestedEnvelope = ReferencedEnvelope.create(gg.getEnvelope(), gg.getCoordinateReferenceSystem());
          if (!gg.getCoordinateReferenceSystem().getName().equals(epsg4326.getName()))
          {
            requestedEnvelope = requestedEnvelope.transform(epsg4326, true);
          }
        }
        catch (Exception e)
        {
          requestedEnvelope = null;
        }

        dim = gg.getGridRange2D().getBounds();
      }
//      else if (name.equals(AbstractGridFormat.INPUT_TRANSPARENT_COLOR.getName()))
//      {
//        System.out.println("Got input transparent color");
//      }
//      else if (name.equals(AbstractGridFormat.BACKGROUND_COLOR.getName()))
//      {
//       System.out.println("Got background color");
//      }
//      else if (name.equals(AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.getName()))
//      {
//        System.out.println("Got geotools write params");
//      }
    }
  }

  MrsImageDataProvider dp = DataProviderFactory
      .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
  final MrsPyramidMetadata meta = dp.getMetadataReader().read();

  final int tilesize = meta.getTilesize();
  final TMSUtils.Bounds bounds;
  int zoom;

  if (requestedEnvelope == null)
  {
    log.fine("No envelope given, calculating bounds");
    bounds = meta.getBounds().getTMSBounds();
    zoom = meta.getMaxZoomLevel();
  }
  else
  {
    bounds = TMSUtils.limit(new TMSUtils.Bounds(requestedEnvelope.getMinX(), requestedEnvelope.getMinY(), requestedEnvelope.getMaxX(), requestedEnvelope.getMaxY()));

    double pw = bounds.width() / dim.getWidth();
    double ph = bounds.height() / dim.getHeight();

    zoom = Math.max(TMSUtils.zoomForPixelSize(pw, tilesize), TMSUtils.zoomForPixelSize(ph, tilesize));
  }

  log.fine("Zoom: " + zoom);
  log.fine("Bounds: " + bounds.toString());
  final MrsPyramid pyramid = MrsPyramid.open(dp);

  final MrsImage image;
  if (pyramid.hasPyramids() && zoom <= meta.getMaxZoomLevel() && zoom > 0)
  {
    image = pyramid.getImage(zoom);
  }
  else
  {
    image = pyramid.getHighestResImage();
    zoom = meta.getMaxZoomLevel();
  }

  try
  {
    TMSUtils.TileBounds tb = TMSUtils.boundsToTile(bounds, zoom, tilesize);
    log.fine("Tile Bounds: " + tb.toString());

    Raster merged = RasterTileMerger.mergeTiles(image, tb);

    TMSUtils.Bounds actualBounds = TMSUtils.tileToBounds(tb, zoom, tilesize);

    TMSUtils.Pixel requestedUL =
        TMSUtils.latLonToPixelsUL(bounds.n, bounds.w, zoom, tilesize);
    TMSUtils.Pixel requestedLR =
        TMSUtils.latLonToPixelsUL(bounds.s, bounds.e, zoom, tilesize);

    TMSUtils.Pixel actualUL =
        TMSUtils.latLonToPixelsUL(actualBounds.n, actualBounds.w, zoom, tilesize);
//      TMSUtils.Pixel actualLR =
//          TMSUtils.latLonToPixelsUL(actualBounds.s, actualBounds.e, zoomLevel, tilesize);

    int offsetX = (int) (requestedUL.px - actualUL.px);
    int offsetY = (int) (requestedUL.py - actualUL.py);

    int croppedW = (int) (requestedLR.px - requestedUL.px);
    int croppedH = (int) (requestedLR.py - requestedUL.py);

    log.fine("Original Crop values: x: " + offsetX + " y: " + offsetY + " w: " + croppedW + " h: " + croppedH);


    if (offsetX < 0)
    {
      offsetX = 0;
      bounds.w = actualBounds.w;
    }

    if (offsetY < 0)
    {
      offsetY = 0;
      bounds.n = actualBounds.n;
    }

    if (offsetX + croppedW > merged.getWidth())
    {
      bounds.e = actualBounds.e;
      croppedW = merged.getWidth() - offsetX;
    }

    if (offsetY + croppedH > merged.getHeight())
    {
      bounds.s = actualBounds.s;
      croppedH = merged.getHeight() - offsetY;
    }

    log.fine("Raw raster: x: " + merged.getMinX() + " y: " + merged.getMinY() + " w: " + merged.getWidth() + " h: " +
        merged.getHeight());

    log.fine("Cropping to: x: " + offsetX + " y: " + offsetY + " w: " + croppedW + " h: " + croppedH);

    final WritableRaster cropped = merged.createCompatibleWritableRaster(croppedW, croppedH);
    cropped.setDataElements(0, 0, croppedW, croppedH, merged.getDataElements(offsetX, offsetY, croppedW, croppedH, null));

    log.fine(
        "Cropped raster: x: " + cropped.getMinX() + " y: " + cropped.getMinY() + " w: " + cropped.getWidth() + " h: " +
            cropped.getHeight());

    final GeneralEnvelope envelope = new GeneralEnvelope(new double[] { bounds.w, bounds.s },
        new double[] { bounds.e, bounds.n});
    envelope.setCoordinateReferenceSystem(epsg4326);

    final BufferedImage img = RasterUtils.makeBufferedImage(cropped);

    final GridCoverageFactory factory = CoverageFactoryFinder.getGridCoverageFactory(null);
    return factory.create(pyramid.getName(), img, envelope);
  }
  catch (Exception e)
  {
    e.printStackTrace();
    throw e;
  }
  finally
  {
    if (image != null)
    {
      image.close();
    }
  }
}

@Override
public double[] getReadingResolutions(String name, OverviewPolicy policy, double[] requestedResolution)
    throws IOException
{

  log.fine("Calculating Reading Resolutions for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  if (requestedResolution == null || requestedResolution.length != 2 )
  {
    throw new IllegalArgumentException("Error calculating resolutions " + name);
  }
  else
  {
    // calculate the actual resolution we'll use for the reading
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    final MrsPyramidMetadata meta = dp.getMetadataReader().read();

    final int tilesize = meta.getTilesize();
    int zoom = Math.max(TMSUtils.zoomForPixelSize(requestedResolution[0], tilesize), TMSUtils.zoomForPixelSize(requestedResolution[1], tilesize));

    double res = TMSUtils.resolution(zoom, tilesize);

    log.fine("Reading Resolutions for: " + name + " " + res);

    return new double[]{res, res};
  }

}

@Override
public GridEnvelope getOriginalGridRange(String name)
{
  log.fine("Getting Grid Range for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  // get the pixel size of the base image
  try
  {
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsPyramidMetadata meta = dp.getMetadataReader().read();

    LongRectangle bounds = meta.getPixelBounds(meta.getMaxZoomLevel());

    log.fine("Grid Range for: " + name + " is " + bounds.toString());

    return new GridEnvelope2D((int)bounds.getMinX(), (int)bounds.getMinY(), (int)bounds.getMaxX(), (int)bounds.getMaxY());
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;

}

@Override
public CoordinateReferenceSystem getCoordinateReferenceSystem(String name)
{
  log.fine("Getting CRS for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  // images are always WGS-84
  return epsg4326;

}

@Override
public GeneralEnvelope getOriginalEnvelope(String name)
{
  log.fine("Getting Envelope for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  // get bounds
  try
  {
    MrsImageDataProvider dp = DataProviderFactory.getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsPyramidMetadata meta = dp.getMetadataReader().read();

    Bounds bounds = meta.getBounds();

    final GeneralEnvelope envelope = new GeneralEnvelope(new double[] { bounds.getMinX(), bounds.getMinY() },
        new double[] { bounds.getMaxX(), bounds.getMaxY()});
    envelope.setCoordinateReferenceSystem(epsg4326);

    log.fine("Envelope for: " + name + " is " + envelope.toString());

    return envelope;
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;
}


@Override
public String[] getGridCoverageNames()
{
  log.fine("Getting coverage names for MrGeo");

  return layers.toArray(new String[layers.size()]);
}





@Override
public String[] getMetadataNames(String name)
{
  log.fine("Getting metadata names for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  return new String[]{};
}

@Override
public String getMetadataValue(String coverageName, String name)
{
  log.fine("Getting metadata value for: " + coverageName + ": " + name);

  if (!checkName(coverageName)) {
    throw new IllegalArgumentException("The specified coverage " + coverageName + "is not found");
  }

  return null;
}

@Override
public Set<ParameterDescriptor<List>> getDynamicParameters(String name) throws IOException
{
  log.fine("Getting dynamic parameters for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  return new HashSet<ParameterDescriptor<List>>();
}

@Override
public DatasetLayout getDatasetLayout(String name)
{
  log.fine("Getting dataset layout for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  return new DefaultDatasetLayoutImpl();
}

@Override
public GridEnvelope getOverviewGridEnvelope(String name, int overviewIndex) throws IOException
{
  log.fine("Getting Overview Grid Range for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  // get the pixel size of the base image
  try
  {
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsPyramidMetadata meta = dp.getMetadataReader().read();

    LongRectangle bounds = meta.getPixelBounds(overviewIndex);

    log.fine("Overview Grid Range for: " + name + " is " + bounds.toString());

    return new GridEnvelope2D((int)bounds.getMinX(), (int)bounds.getMinY(), (int)bounds.getMaxX(), (int)bounds.getMaxY());
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;
}

@Override
public ImageLayout getImageLayout(String name) throws IOException
{
  log.fine("Getting JAI layout for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  // get the pixel size of the base image
  try
  {
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsPyramidMetadata meta = dp.getMetadataReader().read();

    LongRectangle bounds = meta.getPixelBounds(meta.getMaxZoomLevel());

    ImageLayout layout = new ImageLayout();
    layout.setMinX(0);
    layout.setMinY(0);
    layout.setWidth((int)bounds.getWidth());
    layout.setHeight((int)bounds.getHeight());

    // only 1 tile!
    layout.setTileGridXOffset(0);
    layout.setTileGridYOffset(0);

    layout.setTileWidth((int)bounds.getWidth());
    layout.setTileHeight((int)bounds.getHeight());

    MrsImageReader r = dp.getMrsTileReader(meta.getMaxZoomLevel());

    final Iterator<Raster> it = r.get();
    try
    {
      Raster raster = it.next();

      layout.setColorModel(RasterUtils.createColorModel(raster));
      layout.setSampleModel(raster.getSampleModel());
    }
    finally
    {
      if (!r.canBeCached() && it instanceof Closeable)
      {
        ((Closeable) it).close();
        r.close();
        r = null;
      }
    }

    return layout;
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;

}

@Override
public double[][] getResolutionLevels(String name) throws IOException
{
  log.fine("Getting resolution levels for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  final double[][] resolutions = new double[1][2];
  double[] hres = getHighestRes(name);

  resolutions[0][0] = hres[0];
  resolutions[0][1] = hres[1];

  return resolutions;
}

@Override
public GroundControlPoints getGroundControlPoints(String name)
{
  log.fine("Getting ground control points for: " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  return null;
}

@Override
protected double[] getHighestRes(String name)
{
  log.fine("Getting highest resolution for : " + name);

  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }

  try
  {
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(name, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsPyramidMetadata meta = dp.getMetadataReader().read();

    double res = TMSUtils.resolution(meta.getMaxZoomLevel(), meta.getTilesize());

    log.fine("Highest resolution for: " + name + " " + res);

    return new double[]{res, res};
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;
}

@Override
public int getGridCoverageCount()
{
  log.fine("Getting coverage count for MrGeo: " + layers.size());

  return layers.size();
}

@Override
public int getNumOverviews(String name)
{
  if (!checkName(name)) {
    throw new IllegalArgumentException("The specified coverage " + name + "is not found");
  }
  log.fine("Getting num overviews for: " + name);

  return 0;
}

}
