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

import org.geoserver.catalog.*;
import org.geoserver.catalog.impl.CoverageDimensionImpl;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.platform.GeoServerExtensions;
import org.geotools.coverage.grid.GeneralGridEnvelope;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.NumberRange;
import org.geotools.util.logging.Logging;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.ProviderProperties;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.image.ImageStats;
import org.mrgeo.image.MrsPyramidMetadata;
import org.mrgeo.utils.LongRectangle;
import org.mrgeo.utils.TMSUtils;
import org.opengis.coverage.SampleDimensionType;
import org.opengis.coverage.grid.GridGeometry;

import java.awt.*;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MrGeoLayerUpdater implements Runnable
{
final static String ENABLE_UPDATE = "enable.update";
private final static String UPDATE_TIME = "update.time";
private final static String UPDATE_TIME_DEFAULT = "300";  // 300 sec (5 min)

private final static String WORKSPACE = "workspace";
private final static String STORE = "coveragestore";
private final static String NAMESPACE = "namespace";

private static final Logger log = Logging.getLogger("org.mrgeo.gce.MrGeoLayerUpdater");

final Properties config;
final int sleep;
final String workspace;
final String coveragestore;
final String namespace;
final ProviderProperties providerProperties;


public MrGeoLayerUpdater(Properties config)
{
  this.config = config;

  providerProperties = new ProviderProperties(config.getProperty(MrGeoReader.USERNAME, ""), config.getProperty(MrGeoReader.USER_ROLES, ""));

  int sleepsec = Integer.parseInt(config.getProperty(UPDATE_TIME, UPDATE_TIME_DEFAULT));

  workspace = config.getProperty(WORKSPACE, "mrgeo");
  coveragestore = config.getProperty(STORE, "mrgeo");
  namespace = config.getProperty(NAMESPACE, workspace);

  sleep = sleepsec * 1000;

  Thread thread = new Thread(this);
  thread.start();

}

@Override
public void run()
{
  long start = System.currentTimeMillis();

  boolean done = false;

  while (!done)
  {
    try
    {
      log.fine("update layers: " + (int) ((System.currentTimeMillis() - start) / 1000));

      // get the catalog
      Catalog catalog = (Catalog) GeoServerExtensions.bean("catalog");

      if (catalog != null)
      {

        CoverageStoreInfo csi = updateStores(catalog);

        if (csi != null)
        {
          updateCoverages(catalog, csi);
        }
        else
        {
          log.warning("can't access coverage store");
        }
      }
      else
      {
        log.warning("can't access catalog");
      }

      // check here, bacause we need to run through all the setup once...
      if (config.getProperty(MrGeoLayerUpdater.ENABLE_UPDATE, "false").equals("false"))
      {
        done = true;
      }
      else
      {
        Thread.sleep(sleep);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      done = true;
    }
  }
}

private void updateCoverages(Catalog catalog, CoverageStoreInfo csi)
{
  try
  {
    Set<String> newImages = new HashSet<>();
    Set<String> oldImages = new HashSet<>();

    Collections.addAll(newImages, DataProviderFactory.listImages(providerProperties));

    for (CoverageInfo ci: catalog.getCoveragesByCoverageStore(csi))
    {
      String name = ci.getNativeCoverageName();
      if (newImages.contains(name)) {
        newImages.remove(name);
      }
      else
      {
        oldImages.add(ci.getName());

        // also need to add layer names, in case they are different than the coverage.  Usually they aren't,
        // but if someone mucked about with them, they could be...
        for (LayerInfo li : catalog.getLayers(ci))
        {
          oldImages.add(li.getName());
        }
      }
    }


    NamespaceInfo nsi = catalog.getNamespaceByPrefix(namespace);
    if (nsi == null)
    {
      nsi = catalog.getFactory().createNamespace();
      nsi.setPrefix(namespace);
      nsi.setURI(namespace);

      catalog.add(nsi);

      log.info("Adding namespace: " + namespace);
    }

    CatalogFacade facade = catalog.getFacade();

    for (String newImage: newImages)
    {
      final MrsImageDataProvider dp = DataProviderFactory.getMrsImageDataProvider(newImage, DataProviderFactory.AccessMode.READ, providerProperties);
      final MrsPyramidMetadata meta = dp.getMetadataReader().read();

      int zoom = meta.getMaxZoomLevel();

      CoverageInfoImpl ci = (CoverageInfoImpl) catalog.getFactory().createCoverage();
      ci.setEnabled(true);

      ci.setName(meta.getPyramid());
      ci.setNativeName(meta.getPyramid());
      ci.setNativeCoverageName(meta.getPyramid());

      TMSUtils.Bounds bounds = meta.getBounds().getTMSBounds();

      final GeneralEnvelope croppedEnvelope = new GeneralEnvelope(
          new double[] { bounds.w, bounds.s }, new double[] { bounds.e, bounds.n });
      croppedEnvelope.setCoordinateReferenceSystem(DefaultGeographicCRS.WGS84);

      LongRectangle lr = meta.getPixelBounds(zoom);

      final GridGeometry gg = new GridGeometry2D(
          new GeneralGridEnvelope(
              new Rectangle((int)lr.getMinX(), (int)lr.getMinY(), (int)lr.getMaxX(), (int)lr.getMaxY())), croppedEnvelope);

      ci.setEnabled(true);
      ci.setGrid(gg);
      ci.setNativeFormat(MrGeoFormat.FORMAT_NAME);
      ci.setAbstract("");
      ci.setAdvertised(true);
      ci.setCatalog(catalog);
      ci.setDescription("");
      ci.setNamespace(nsi);

      ReferencedEnvelope bb = ReferencedEnvelope.create(croppedEnvelope, DefaultGeographicCRS.WGS84);
      ci.setLatLonBoundingBox(bb);
      ci.setNativeBoundingBox(bb);
      ci.setNativeCRS(DefaultGeographicCRS.WGS84);
      ci.setProjectionPolicy(ProjectionPolicy.FORCE_DECLARED);
      ci.setSRS("EPSG:4326");
      ci.setStore(csi);
      ci.setTitle(meta.getPyramid());

      ci.setNativeFormat("GEOTIFF");


      LinkedList<String> fmtList = new LinkedList<>();
      fmtList.add("GIF");
      fmtList.add("PNG");
      fmtList.add("JPEG");
      fmtList.add("TIFF");
      fmtList.add("ImageMosaic");
      fmtList.add("GEOTIFF");
      fmtList.add("ArcGrid");
      fmtList.add("Gtopo30");

      ci.setSupportedFormats(fmtList);

      LinkedList<String> srsList = new LinkedList<>();
      srsList.add("EPSG:4326");
      ci.setRequestSRS(srsList);
      ci.setResponseSRS(srsList);

      ArrayList<CoverageDimensionInfo> dims  = new ArrayList<>(3);
      for (int b = 0; b < meta.getBands(); b++)
      {
        ImageStats stats = meta.getStats(b);

        CoverageDimensionImpl cdi = (CoverageDimensionImpl) catalog.getFactory().createCoverageDimension();
        cdi.setName("band " + b);

        LinkedList<Double> dblList = new LinkedList<>();
        dblList.add(meta.getDefaultValue(b));
        cdi.setNullValues(dblList);

        if (stats != null)
        {
          NumberRange<Double> minmax = NumberRange.create(stats.min, stats.max);
          cdi.setRange(minmax);
        }
        switch (meta.getTileType()) {
        case DataBuffer.TYPE_BYTE:
          cdi.setDimensionType(SampleDimensionType.UNSIGNED_8BITS);
          cdi.setDescription("Band " + (b + 1) + " (byte)");
          break;
        case DataBuffer.TYPE_SHORT:
          cdi.setDimensionType(SampleDimensionType.SIGNED_16BITS);
          cdi.setDescription("Band " + (b + 1) + " (short)");
          break;
        case DataBuffer.TYPE_USHORT:
          cdi.setDimensionType(SampleDimensionType.UNSIGNED_16BITS);
          cdi.setDescription("Band " + (b + 1) + " (unsigned short)");
          break;
        case DataBuffer.TYPE_INT:
          cdi.setDimensionType(SampleDimensionType.SIGNED_32BITS);
          cdi.setDescription("Band " + (b + 1) + " (int)");
          break;
        case DataBuffer.TYPE_FLOAT:
          cdi.setDimensionType(SampleDimensionType.REAL_32BITS);
          cdi.setDescription("Band " + (b + 1) + " (float)");
          break;
        case DataBuffer.TYPE_DOUBLE:
          cdi.setDimensionType(SampleDimensionType.REAL_64BITS);
          cdi.setDescription("Band " + (b + 1) + " (double)");
          break;
        }

        dims.add(cdi);
      }

      ci.setDimensions(dims);

      ValidationResult valid = catalog.validate(ci, true);
      if (valid.isValid())
      {
        log.info("Adding Coverage: " + meta.getPyramid());

        // NOTE:  There is a bug in GeoServer (at least the 2.8.x versions) where the add w/ CoverageInfo
        // isn't performing the synchronize on it's facade.  Luckly, I can get the facade (it is above) and
        // sync here.  It works like a charm!
        synchronized (facade)
        {
          catalog.add(ci);
        }

        LayerInfoImpl li = (LayerInfoImpl) catalog.getFactory().createLayer();
        li.setResource(ci);
        li.setEnabled(true);
        li.setName(ci.getName());
        li.setType(PublishedType.RASTER);
        li.setPath("/");
        StyleInfo style = new CatalogBuilder(catalog).getDefaultStyle(li.getResource());
        li.setDefaultStyle(style);

        valid = catalog.validate(li, true);
        if (valid.isValid())
        {
          catalog.add(li);
          log.fine("Adding Layer: " + meta.getPyramid());
        }
        else
        {
          log.severe("Invalid Layer: " + meta.getPyramid());
        }
      }
      else
      {
        log.severe("Invalid Coverage: " + meta.getPyramid());
      }
    }

    for (String old: oldImages)
    {
      LayerInfo li = catalog.getLayerByName(old);
      if (li != null)
      {
        catalog.remove(li);
      }

      CoverageInfo ci = catalog.getCoverageByName(old);
      if (ci != null)
      {
        catalog.remove(ci);
      }
    }

  }
  catch (IOException e)
  {
    e.printStackTrace();
  }
}

private CoverageStoreInfo updateStores(Catalog catalog)
{

  if (log.isLoggable(Level.FINE))
  {
    log.fine("Workspaces: ");
    for (WorkspaceInfo wi : catalog.getWorkspaces())
    {
      log.fine("  " + wi.getName());
    }
    log.fine("DataStores: ");
    for (DataStoreInfo di : catalog.getDataStores())
    {
      log.fine("  " + di.getName() + " (" + di.getWorkspace().getName() + ")");
    }
    log.fine("Layers: ");
    for (LayerInfo li : catalog.getLayers())
    {
      log.fine("  " + li.getName() + " (" + li.getResource().getName() + ")");
    }

    log.fine("CoverageStores: ");
    for (CoverageStoreInfo csi : catalog.getCoverageStores())
    {
      log.fine("  " + csi.getName() + " (" + csi.getWorkspace().getName() + ")");
    }
    log.fine("Coverages: ");
    for (CoverageInfo ci : catalog.getCoverages())
    {
      log.fine("  " + ci.getName() + " (" + ci.getStore().getName() + ")");
    }
  }

  WorkspaceInfo winfo = catalog.getWorkspaceByName(workspace);
//  System.out.println("workspace: " + (winfo == null ? "null" : winfo.toString()));

  if (winfo == null)
  {
    winfo = addWorkspace(catalog);
  }

  CoverageStoreInfo sinfo = catalog.getCoverageStoreByName(workspace, coveragestore);

//  System.out.println("coveragestore: " + (sinfo == null ? "null" : sinfo.toString()));
  if (sinfo == null)
  {
    sinfo = addCoverageStore(catalog, winfo);
  }

  return sinfo;

}

private CoverageStoreInfo addCoverageStore(Catalog catalog, WorkspaceInfo winfo)
{
  CoverageStoreInfo csi = catalog.getFactory().createCoverageStore();
  csi.setName(coveragestore);

  csi.setURL(config.getProperty(MrGeoFormatFactorySpi.CONFIG_FILE, "mrgeo.config"));
  csi.setDescription("MrGeo data source, automatically (periodically) updated with new layers");
  csi.setType(MrGeoFormat.FORMAT_NAME);
  csi.setEnabled(true);
  csi.setWorkspace(winfo);

  log.info("Adding CoverageStore: " + csi.getName());
  try
  {
    catalog.add(csi);
  }
  catch (Exception e)
  {
    log.severe("Error adding coverage store \"" + csi.getName() + "\" to catalog");
    e.printStackTrace();
  }

  return csi;
}

private WorkspaceInfo addWorkspace(Catalog catalog)
{
  WorkspaceInfo winfo = catalog.getFactory().createWorkspace();
  winfo.setName(workspace);

  log.info("Adding Workspace: " + winfo.getName());
  try
  {
    catalog.add(winfo);
  }
  catch (Exception e)
  {
    log.severe("Error adding workspace \"" + workspace + "\" to catalog");
    e.printStackTrace();
  }

  return winfo;
}

}
