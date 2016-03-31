MrGeo Geoserver Plugin is an open source software data plugin for the [GeoServer](http://geoserver.org) package.
It allows Geoserver to communicate directly with MrGeo data sources


## License

MrGeo Geoserver Plugin licensed under the [GPL v2.0](http://www.gnu.org/licenses/old-licenses/gpl-2.0.html).

## Using
This plugin is intended to be used as part of an existing GeoServer instance.  Setting up GeoServer is out of the scope of this document.

1. Make sure these environment variables are available to the web container you are running.  For Tomcat, add them to _TOMCAT_HOME/bin/setenv.sh_
  *
```bash
  MRGEO_COMMON_HOME=/usr/local/mrgeo
  MRGEO_CONF_DIR=/usr/local/mrgeo/conf
  HADOOP_CONF_DIR=/usr/local/hadoop/conf
```
  * for Tomcat, add these:
```bash
  export MRGEO_COMMON_HOME=/usr/local/mrgeo
  export MRGEO_CONF_DIR=/usr/local/mrgeo/conf
  export HADOOP_CONF_DIR=/usr/local/hadoop/conf
  export CLASSPATH=\$MRGEO_COMMON_HOME:\$MRGEO_CONF_DIR:\$HADOOP_CONF_DIR
  export CATALINA_OPTS="-DMRGEO_COMMON_HOME=/usr/local/mrgeo " \
                       "-DMRGEO_CONF_DIR=/usr/local/hadoop/conf " \
                       "-DHADOOP_CONF_DIR=/usr/local/hadoop " \
                       "-DGEOSERVER_DATA_DIR=/mnt/geoserver-data " \
                       "-DGEOWEBCACHE_CACHE_DIR=/mnt/geoserver-cache " \
                       "-Xms512m -Xmx12G " \
                       "-XX:NewSize=256m -XX:MaxNewSize=256m " \
                       "-XX:PermSize=256m -XX:MaxPermSize=256m -XX:+DisableExplicitGC"

```
Note:  The Java options for CATALINA_OPTS allow for more memory for tomcat to run.  Your milage may vary.
1. The environment variables should also be available on the classpath as well.

## Building

MrGeo Geoserve Plugin uses [Apache Maven](http://maven.apache.org/) for a build system.

    % mvn clean package


## Contributing

All pull request contributions to this project will be released under the GPL 2.0 license.

## More Information

TBD



