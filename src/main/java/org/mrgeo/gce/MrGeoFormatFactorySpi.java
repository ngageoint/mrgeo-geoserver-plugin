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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFactorySpi;
import org.geotools.util.logging.Logging;
import org.mrgeo.core.MrGeoConstants;
import org.mrgeo.core.MrGeoProperties;
import org.mrgeo.hdfs.utils.HadoopFileUtils;
import org.mrgeo.utils.LoggingUtils;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
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
    //LoggingUtils.setLogLevel("org.mrgeo", LoggingUtils.DEBUG);
    if (log.isLoggable(Level.FINE))
    {
      try
      {
        log.fine("MrGeo environment:");
        String env = System.getenv("MRGEO_HOME");
        log.fine("  MRGEO_HOME: " + ((env == null) ? "null" : env));
        env = System.getenv("HADOOP_CONF_DIR");
        log.fine("  HADOOP_CONF_DIR: " + ((env == null) ? "null" : env));
        FileSystem fs = HadoopFileUtils.getFileSystem();
        log.fine("  Default hadoop filesystem: " + ((fs == null) ? "null" : fs.getClass().getSimpleName()));

        if (fs != null)
        {
          String dir = MrGeoProperties.getInstance().getProperty(MrGeoConstants.MRGEO_HDFS_IMAGE, "/mrgeo/images");
            try
            {
              log.fine("  Files in " + dir + ":");
              fs = HadoopFileUtils.getFileSystem(new Path(dir));
              log.fine("      (" + fs.getClass().getSimpleName() + ")");

              FileStatus[] files = fs.listStatus(new Path(dir));
              if (files == null)
              {
                log.fine("    <none>");
              }
              else
              {
                for (FileStatus file : files)
                {
                  log.fine("    " + file.toString());
                }
              }
            }
            catch (Exception e)
            {
              log.severe("Exception while getting files:");
              log.severe(e.getMessage());
              logstack(e);
              e.printStackTrace();
            }
            catch (Throwable t)
            {
              log.severe("Throwable (exception) while getting files:");
              log.severe(t.toString());
              logstack(t);
              t.printStackTrace();
            }
          }
      }
      catch (Exception e)
      {
        log.severe("Exception while getting environment:");
        log.severe(e.getMessage());

        logstack(e);
        e.printStackTrace();
      }
      catch (Throwable t)
      {
        log.severe("Throwable (exception) while getting environment:");
        log.severe(t.toString());
        logstack(t);
        t.printStackTrace();
      }

    }

    GeoServerResourceLoader loader = (GeoServerResourceLoader) GeoServerExtensions.bean("resourceLoader");
    File file = loader.find(CONFIG_FILE);

    if (file != null && file.exists())
    {
      InputStream in = new FileInputStream(file);
      config.load(in);

      config.setProperty(CONFIG_FILE, file.getCanonicalPath());

      if (updater == null)
      {
        updater = new MrGeoLayerUpdater(config);
      }
    }
    else
    {
      log.warning("Can't find the MrGeo config file: " + CONFIG_FILE);
    }
  }
  catch (Exception e)
  {
    log.severe("Exception in constructor:");
    log.severe(e.getMessage());
    logstack(e);

    e.printStackTrace();
  }
  catch (Throwable t)
  {
    log.severe("Throwable (exception) in constructor:");
    log.severe(t.getMessage());
    logstack(t);

    t.printStackTrace();
  }
  finally
  {
    log.fine("Made it to the end of the constructor (" + this.getClass().getSimpleName() + ")");
  }
}

private static void logstack(Throwable t)
{
  StackTraceElement[] st = t.getStackTrace();
  boolean first = true;
  for (StackTraceElement tr: st)
  {
    if (first)
    {
      first = false;
      log.severe(tr.toString());
    }
    else
    {
      log.severe("  " + tr.toString());
    }
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