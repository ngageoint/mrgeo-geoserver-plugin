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

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFactorySpi;
import org.geotools.util.logging.Logging;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class MrGeoFormatFactorySpi implements GridFormatFactorySpi
{
static final String CONFIG_FILE = "mrgeo.config";

private static final Logger log = Logging.getLogger("org.mrgeo.gce.MrGeoFormatFactorySpi");

private static MrGeoLayerUpdater updater = null;
private Properties config = new Properties();


public MrGeoFormatFactorySpi()
{
  try
  {
    GeoServerResourceLoader loader = (GeoServerResourceLoader) GeoServerExtensions.bean("resourceLoader");
    File file = loader.find(CONFIG_FILE);

    if (file.exists())
    {
      InputStream in = new FileInputStream(file);
      config.load(in);

      config.setProperty(CONFIG_FILE, file.getCanonicalPath());

      if (updater == null)
      {
        updater = new MrGeoLayerUpdater(config);
      }
    }
  }
  catch (Exception ignored)
  {

  }
}

public AbstractGridFormat createFormat()
{
  return new MrGeoFormat(config);
}

public boolean isAvailable()
{
  boolean available = true;


  // Check if mrgeo is available...
  try {
    Class.forName("org.mrgeo.data.DataProviderFactory");
  } catch (ClassNotFoundException cnf) {
    available = false;
  }

  if (available)
    log.info("MrGeo format is available");
  else
    log.severe("MrGeo format is NOT available");

  return available;
}

public Map<RenderingHints.Key, ?> getImplementationHints()
{
  return Collections.emptyMap();
}

}