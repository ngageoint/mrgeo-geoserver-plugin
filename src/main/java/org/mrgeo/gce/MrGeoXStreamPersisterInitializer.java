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

import com.thoughtworks.xstream.XStream;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterInitializer;
import org.geotools.util.logging.Logging;

import java.util.LinkedList;
import java.util.logging.Logger;

class MrGeoXStreamPersisterInitializer implements XStreamPersisterInitializer
{
private static final Logger log = Logging.getLogger("org.mrgeo.gce.MrGeoXStreamPersisterInitializer");

public MrGeoXStreamPersisterInitializer()
{
  log.info("Creating MrGeoXStreamPersisterInitializer");
}

@Override
public void init(XStreamPersister persister)
{
  XStream xs = persister.getXStream();
  xs.allowTypes(new Class[]{
      LinkedList.class
  });
}
}
