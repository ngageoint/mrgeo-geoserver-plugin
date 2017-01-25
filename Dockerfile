FROM ubuntu:14.04


ENV MRGEO_COMMON_HOME /usr/local/mrgeo
ENV MRGEO_CONF_DIR /usr/local/mrgeo/conf
ENV HADOOP_CONF_DIR /usr/local/hadoop/conf
ENV GEOSERVER_DATA_DIR /mnt/geoserver-data
ENV GEOWEBCACHE_CACHE_DIR /mnt/geoserver-cache
ENV CLASSPATH $MRGEO_COMMON_HOME:$MRGEO_CONF_DIR:$HADOOP_CONF_DIR

ENV CATALINA_OPTS "-DMRGEO_COMMON_HOME=/usr/local/mrgeo -DMRGEO_CONF_DIR=/usr/local/hadoop/conf -DHADOOP_CONF_DIR=/usr/local/hadoop -DGEOSERVER_DATA_DIR=/mnt/geoserver-data  -DGEOWEBCACHE_CACHE_DIR=/mnt/geoserver-cache -Xms512m -Xmx4G -XX:NewSize=256m -XX:MaxNewSize=256m -XX:PermSize=256m -XX:MaxPermSize=256m -XX:+DisableExplicitGC"

ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:/usr/local/apache-maven-3.3.9/bin:$PATH

ENV TOMCAT_TGZ_URL https://archive.apache.org/dist/tomcat/tomcat-8/v8.5.5/bin/apache-tomcat-8.5.5.tar.gz


WORKDIR $CATALINA_HOME

# install java 8
RUN \
  apt-get update && \
  apt-key adv --keyserver keyserver.ubuntu.com --recv-keys DA1A4A13543B466853BAF164EB9B1D8886F44E2A && \
  touch /etc/apt/sources.list.d/openjdk.list && \
  echo "deb http://ppa.launchpad.net/openjdk-r/ppa/ubuntu trusty main " >>/etc/apt/sources.list.d/openjdk.list && \
  echo "deb-src http://ppa.launchpad.net/openjdk-r/ppa/ubuntu trusty main" >>/etc/apt/sources.list.d/openjdk.list && \
  apt-get update && \
  apt-get -y install openjdk-8-jdk

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH $JAVA_HOME/bin:$PATH


# install misc
RUN \
  apt-get install wget -y && \
  update-ca-certificates -f

# install tomcat
RUN \
  wget -O tomcat.tar.gz "$TOMCAT_TGZ_URL" && \
  tar -xvf tomcat.tar.gz --strip-components=1 && \
  rm tomcat.tar.gz


# RUN apt-get install maven -y
RUN \
  wget -O apache-maven-3.3.9-bin.tar.gz http://mirror.metrocast.net/apache/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz && \
  tar xzvf apache-maven-3.3.9-bin.tar.gz -C /usr/local && \
  rm -f apache-maven-3.3.9-bin.tar.gz


RUN mkdir -p /usr/local/src/mrgeo-geoserver-plugin
COPY pom.xml /usr/local/src/mrgeo-geoserver-plugin

RUN \
  cd /usr/local/src/mrgeo-geoserver-plugin && \
  mvn verify clean --fail-never

COPY . /usr/local/src/mrgeo-geoserver-plugin

# build mrgeo-geoserver-plugin
RUN \
  cd /usr/local/src/mrgeo-geoserver-plugin && \
  mvn package

RUN apt-get install unzip -y

RUN \
  cd /tmp && \
  wget -O geoserver-2.9.0-war.zip https://downloads.sourceforge.net/project/geoserver/GeoServer/2.9.0/geoserver-2.9.0-war.zip && \
  unzip geoserver-2.9.0-war.zip && \
  mv geoserver.war /usr/local/tomcat/webapps && \
  rm geoserver-2.9.0-war.zip && \
  cd /usr/local/tomcat/webapps && \
  unzip geoserver.war -d geoserver && \
  rm geoserver.war


RUN \
  mkdir -p $MRGEO_CONF_DIR && \
  mkdir -p $GEOSERVER_DATA_DIR && \
  mkdir -p $GEOWEBCACHE_CACHE_DIR && \
  mkdir -p $HADOOP_CONF_DIR

COPY conf/mrgeo/mrgeo.conf.example $MRGEO_CONF_DIR

COPY conf/hadoop/* $HADOOP_CONF_DIR/

RUN \
  cd /usr/local/src/mrgeo-geoserver-plugin/target/gt-mrgeo-1.1-1.2.0-emr4.7.1-SNAPSHOT && \
  cp * /usr/local/tomcat/webapps/geoserver/WEB-INF/lib

COPY conf/tomcat/* /usr/local/tomcat/conf/

EXPOSE 8080
CMD ["catalina.sh", "run"]
