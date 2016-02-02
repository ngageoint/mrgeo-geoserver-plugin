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

import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.factory.Hints;
import org.geotools.parameter.DefaultParameterDescriptorGroup;
import org.geotools.parameter.ParameterGroup;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.ParameterValueGroup;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

public class MrGeoFormat extends AbstractGridFormat implements Format
{

public static final String FORMAT_NAME = "MrGeo";

private static final Logger log = Logging.getLogger("org.mrgeo.gce.MrGeoFormat");

private Properties config;

public MrGeoFormat(Properties config)
{
  this.config = config;
  // information for this format
  HashMap<String, String> info = new HashMap<String, String>();

  info.put("name", FORMAT_NAME);
  info.put("description","A big-data raster image stored in MrGeo format");
  info.put("vendor", "MrGeo");
  info.put("docURL", "http://github.com/ngageoint/mrgeo");
  info.put("version", "0.1");
  mInfo = info;


  // reading parameters
  readParameters = new ParameterGroup(
      new DefaultParameterDescriptorGroup(
          mInfo,
          new GeneralParameterDescriptor[] { READ_GRIDGEOMETRY2D, INPUT_TRANSPARENT_COLOR, BACKGROUND_COLOR}));

}

@Override
public boolean accepts(Object source, Hints hints)
{
  File configfile = null;
  if (source instanceof URI)
  {
    configfile = new File((URI) source);
  }
  else if (source instanceof String)
  {
    configfile = new File(source.toString());
  }
  else if (source instanceof File)
  {
    configfile = (File)source;
  }
  else
  {
    return false;
  }

  return configfile.exists();
}


@Override
public ParameterValueGroup getReadParameters()
{
  return super.getReadParameters();
}

@Override
public AbstractGridCoverage2DReader getReader(Object source)
{
  return getReader(source, null);
}

@Override
public AbstractGridCoverage2DReader getReader(Object source, Hints hints)
{File configfile = null;
  if (source instanceof URI)
  {
    configfile = new File((URI) source);
  }
  else if (source instanceof String)
  {
    configfile = new File(source.toString());
  }
  else if (source instanceof File)
  {
    configfile = (File)source;
  }

  if (configfile != null)
  {
    try
    {
      return new MrGeoReader(config);
    }
    catch (Exception ignored)
    {
      ignored.printStackTrace();
    }
  }

  log.severe("Can't create MrGeoReader.  (config: " + (configfile == null ? "null" : configfile.toURI().toString()) + ")");
  return null;
}

@Override
public GeoToolsWriteParams getDefaultImageIOWriteParameters()
{
  throw new UnsupportedOperationException("MrGeo format is read-only.");
}

@Override
public GridCoverageWriter getWriter(Object destination)
{
  throw new UnsupportedOperationException("MrGeo format is read-only.");
}

@Override
public GridCoverageWriter getWriter(Object destination, Hints hints)
{
  throw new UnsupportedOperationException("MrGeo format is read-only.");
}

}
