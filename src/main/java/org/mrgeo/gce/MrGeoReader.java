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
import org.geotools.coverage.CoverageFactoryFinder;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.*;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.logging.Logging;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.data.raster.RasterUtils;
import org.mrgeo.image.MrsImage;
import org.mrgeo.image.MrsImagePyramid;
import org.mrgeo.image.MrsImagePyramidMetadata;
import org.mrgeo.image.RasterTileMerger;
import org.mrgeo.utils.Bounds;
import org.mrgeo.utils.LongRectangle;
import org.mrgeo.utils.TMSUtils;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.media.jai.ImageLayout;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
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

public MrGeoReader(Properties config) throws IOException
{
  this.config = config;

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
protected boolean checkName(String coverageName)
{
  return layers.contains(coverageName);
}

@Override
public GridCoverage2D read(GeneralParameterValue[] parameters) throws IOException
{
  throw new UnsupportedOperationException("MrGeoReader does not have a single coverage mode!");
}

@Override
public GridCoverage2D read(String coverageName, GeneralParameterValue[] parameters) throws IOException
{
  log.info("Reading coverage: " + coverageName);

  ReferencedEnvelope requestedEnvelope = null;
  Rectangle dim = null;

  if (parameters != null) {
    for (GeneralParameterValue parameter : parameters)
    {
      final ParameterValue param = (ParameterValue) parameter;
      final ReferenceIdentifier name = param.getDescriptor().getName();
      if (name.equals(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName()))
      {
        final GridGeometry2D gg = (GridGeometry2D) param.getValue();
        try
        {
          requestedEnvelope = ReferencedEnvelope.create(gg.getEnvelope(), gg.getCoordinateReferenceSystem());
          if (!gg.getCoordinateReferenceSystem().getName().equals(DefaultGeographicCRS.WGS84.getName()))
          {
            requestedEnvelope = requestedEnvelope.transform(DefaultGeographicCRS.WGS84, true);
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
      .getMrsImageDataProvider(coverageName, DataProviderFactory.AccessMode.READ, providerProperties);
  final MrsImagePyramidMetadata meta = dp.getMetadataReader().read();

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
    bounds = new TMSUtils.Bounds(requestedEnvelope.getMinX(), requestedEnvelope.getMinY(), requestedEnvelope.getMaxX(), requestedEnvelope.getMaxY());

    double pw = bounds.width() / dim.getWidth();
    double ph = bounds.height() / dim.getHeight();

    zoom = Math.max(TMSUtils.zoomForPixelSize(pw, tilesize), TMSUtils.zoomForPixelSize(ph, tilesize));
  }

  log.fine("Zoom: " + zoom);
  log.fine("Bounds: " + bounds.toString());
  final MrsImagePyramid pyramid = MrsImagePyramid.open(dp);

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

    log.fine("Raw raster: x: " + merged.getMinX() + " y: " + merged.getMinY() + " w: " + merged.getWidth() + " h: " +
        merged.getHeight());

    final WritableRaster cropped = merged.createCompatibleWritableRaster(croppedW, croppedH);
    cropped.setDataElements(0, 0, croppedW, croppedH, merged.getDataElements(offsetX, offsetY, croppedW, croppedH, null));

    log.fine(
        "Cropped raster: x: " + cropped.getMinX() + " y: " + cropped.getMinY() + " w: " + cropped.getWidth() + " h: " +
            cropped.getHeight());

    final GeneralEnvelope envelope = new GeneralEnvelope(new double[] { bounds.w, bounds.s },
        new double[] { bounds.e, bounds.n});
    envelope.setCoordinateReferenceSystem(DefaultGeographicCRS.WGS84);

    final BufferedImage img = RasterUtils.makeBufferedImage(cropped);

    final GridCoverageFactory factory = CoverageFactoryFinder.getGridCoverageFactory(null);
    return factory.create(pyramid.getName(), img, envelope);
  }
  finally
  {
    image.close();
  }
}

@Override
public double[] getReadingResolutions(String coverageName, OverviewPolicy policy, double[] requestedResolution)
    throws IOException
{
  if (requestedResolution == null || requestedResolution.length != 2 )
  {
    return super.getReadingResolutions(coverageName, policy, requestedResolution);
  }
  else
  {
    // calculate the actual resolution we'll use for the reading
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(coverageName, DataProviderFactory.AccessMode.READ, providerProperties);
    final MrsImagePyramidMetadata meta = dp.getMetadataReader().read();

    final int tilesize = meta.getTilesize();
    int zoom = Math.max(TMSUtils.zoomForPixelSize(requestedResolution[0], tilesize), TMSUtils.zoomForPixelSize(requestedResolution[1], tilesize));

    double res = TMSUtils.resolution(zoom, tilesize);

    return new double[]{res, res};
  }

}

@Override
public GridEnvelope getOriginalGridRange(String coverageName)
{
  // get the pixel size of the base image
  try
  {
    MrsImageDataProvider dp = DataProviderFactory
        .getMrsImageDataProvider(coverageName, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsImagePyramidMetadata meta = dp.getMetadataReader().read();

    LongRectangle bounds = meta.getPixelBounds(meta.getMaxZoomLevel());

    return new GridEnvelope2D((int)bounds.getMinX(), (int)bounds.getMinY(), (int)bounds.getMaxX(), (int)bounds.getMaxY());
  }
  catch (IOException e)
  {
    e.printStackTrace();
  }

  return null;

}

@Override
public CoordinateReferenceSystem getCoordinateReferenceSystem(String coverageName)
{
  // images are always WGS-84
  return DefaultGeographicCRS.WGS84;
}

@Override
public GeneralEnvelope getOriginalEnvelope(String coverageName)
{
  // get bounds
  try
  {
    MrsImageDataProvider dp = DataProviderFactory.getMrsImageDataProvider(coverageName, DataProviderFactory.AccessMode.READ, providerProperties);
    MrsImagePyramidMetadata meta = dp.getMetadataReader().read();

    Bounds bounds = meta.getBounds();

    final GeneralEnvelope envelope = new GeneralEnvelope(new double[] { bounds.getMinX(), bounds.getMinY() },
        new double[] { bounds.getMaxX(), bounds.getMaxY()});
    envelope.setCoordinateReferenceSystem(DefaultGeographicCRS.WGS84);

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
  return layers.toArray(new String[layers.size()]);
}

@Override
public String[] getMetadataNames(String coverageName)
{
  // no metadata, use the super version
  return super.getMetadataNames(coverageName);
}

@Override
public String getMetadataValue(String coverageName, String name)
{
  // no metadata, use the super version
  return super.getMetadataValue(coverageName, name);
}

@Override
public int getGridCoverageCount()
{
  return layers.size();
}

@Override
public Set<ParameterDescriptor<List>> getDynamicParameters(String coverageName) throws IOException
{
  // no dynamic parameters, use the super version
  return super.getDynamicParameters(coverageName);
}

@Override
public int getNumOverviews(String coverageName)
{
  return 0;
}

@Override
public DatasetLayout getDatasetLayout(String coverageName)
{
  return super.getDatasetLayout(coverageName);
}

@Override
public GridEnvelope getOverviewGridEnvelope(String coverageName, int overviewIndex) throws IOException
{
  return super.getOverviewGridEnvelope(coverageName, overviewIndex);
}

@Override
public ImageLayout getImageLayout(String coverageName) throws IOException
{
  return super.getImageLayout(coverageName);
}

@Override
public double[][] getResolutionLevels(String coverageName) throws IOException
{
  return super.getResolutionLevels(coverageName);
}

@Override
protected double[] getHighestRes(String coverageName)
{
  return super.getHighestRes(coverageName);
}

@Override
public GroundControlPoints getGroundControlPoints(String coverageName)
{
  return super.getGroundControlPoints(coverageName);
}

@Override
protected AffineTransform getRescaledRasterToModel(RenderedImage coverageRaster)
{
  return super.getRescaledRasterToModel(coverageRaster);
}
}
