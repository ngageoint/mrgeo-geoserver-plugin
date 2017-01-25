# Docker 

## Build 

```
docker build . -t mrgeo-geoserver-plugin:latest
```

## Run 

```
docker run -p 8080:8080 --name=mrgeo_geoserver_plugin mrgeo-geoserver-plugin:latest
```

## Remove container

```
docker rm /mrgeo_geoserver_plugin
```


