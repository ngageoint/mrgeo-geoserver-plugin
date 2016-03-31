MrGeo Geoserver Plugin is an open source software data plugin for the [GeoServer](http://geoserver.org) package.
It allows Geoserver to communicate directly with MrGeo data sources


## License

MrGeo Geoserver Plugin licensed under the [GPL v2.0](http://www.gnu.org/licenses/old-licenses/gpl-2.0.html).

## Using
This plugin is intended to be used as part of an existing GeoServer instance.  Setting up GeoServer is out of the scope of this document.


## Building

MrGeo Geoserve Plugin uses [Apache Maven](http://maven.apache.org/) for a build system.

    % mvn clean package

Here are the version variables that may need to be set according to your particular build situation.  They can be set by adding `-D<variable>=<value>` to the maven build line.

 | Variable | Default Value | Notes |
 | -------- | ------------- | ----- |


## Installing

1. Make sure these environment variables are available to the web container you are running.  For Tomcat, add them to _TOMCAT_HOME/bin/setenv.sh_. Note: The environment variables should also be available on the classpath as well.

  * Variables
  ```bash
  MRGEO_COMMON_HOME=/usr/local/mrgeo
  MRGEO_CONF_DIR=/usr/local/mrgeo/conf
  HADOOP_CONF_DIR=/usr/local/hadoop/conf
  ```
  * For Tomcat, add these to _TOMCAT_HOME/bin/setenv.sh_.
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
1. Take the _gt-mrgeo-1.0-<version>-SNAPSHOT.tar.gz_ file built previously, and unpack it into the geoserver _WEB-INF/lib_ directory.  Care has been taken to exclude any duplicate, but version different, jars used between MrGeo and GeoServer.  However, changes in the various versions of both may have allowed dupliclates to slip in.  The first time unpacking, you may want to make sure none exist.

1. Copy a _mrgeo.config_ into the geoserver _data_ directory.  If you wish to use defaults for all the config options, an empty file can be created.

1. Copy _mrgeo.conf_ to the _MRGEO_CONF_DIR_ directory (e.g. _/usr/local/mrgeo/conf_)
  * Modify _mrgeo.conf_ to point to the correct data location
  ```
  image.base = /mrgeo/images
  vector.base = /mrgeo/vectors
  kml.base = /mrgeo/kml
  tsv.base = /mrgeo/tsv
  colorscale.base = /mrgeo/color-scales
  ```
  If you are using AWS S3 for data storage, you can use the addresses here as well. i.e. `s3://mrgeo/images`


1. Copy hadoop configurations to _HADOOP_CONF_DIR_ (e.g. _/usr/local/hadoop/conf_).  While any version of Hadoop's configuration files should be OK, it is probably best to use the same version as used to build MrGeo.
  * Files needed are: `core-site.xml`,  `hadoop-env.sh`,  `hdfs-site.xml`, `jets3t.properties`, `mapred-site.xml`, and `yarn-site.xml`.  If you are using S3 for data storage, you can start with the default files and add the changes noted below
  * If using S3 for data storage, add the S3 key/secret key to Hadoop's `core-site.xml`
  ```
  <property>
    <name>fs.s3.awsAccessKeyId</name>
    <value><key</value>
  </property>

  <property>
    <name>fs.s3.awsSecretAccessKey</name>
    <value><secret key></value>
  </property>

  <property>
    <name>fs.s3n.awsAccessKeyId</name>
    <value><key></value>
  </property>

  <property>
    <name>fs.s3n.awsSecretAccessKey</name>
    <value><secret key></value>
  </property>
  ```

1. That should be it, restart the web container and MrGeo should be integrated with GeoServer

## MrGeo Config Options

The _mrgeo.config_ file is used to supply the MrGeo Plugin with configuration options.  This is a standard Java-style properties file, so values are in <key> = <value> format.

NOTE:  For now, the mrgeo plugin automaticly checks and updates the MrGeo Layers ONCE at startup, no matter what the config options say.  This behaviour may change in the future.

| Name | Default | Description |
| ---- | ------- | ----------- |
| enable.update | false | Enable periodic updates to the layers.  If **true**, the plugin will periodically check MrGeo and add or remove layers as needed |
| update.time | 300 | Seconds to periodically check MrGeo for updated layers |
| workspace | mrgeo | Workspace name for MrGeo layers |
| coveragestore | mrgeo | Coverage (store) name for the MrGeo layers |
| namespace | _workspace_ | Namespace name for the MrGeo layers|
| user.name | _none_ | User name for accessing MrGeo layers (This is normally not set, as there are no user names/roles set on layers) |
| user.roles | _none_ | User roles for accessing MrGeo layers (This is normally not set, as there are no user names/roles set on layers) |

## Contributing

All pull request contributions to this project will be released under the GPL 2.0 license.

## More Information

TBD



