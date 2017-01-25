# Docker 

## Build 

```
docker build . -t mrgeo-geoserver-plugin:latest
```

## Run 

```
docker run -p 8080:8080 --name=mrgeo_geoserver_plugin mrgeo-geoserver-plugin:latest
```

## Run a shell in the container

```
docker exec -i -t mrgeo_geoserver_plugin /bin/bash
```


## Remove container

```
docker rm /mrgeo_geoserver_plugin
```


