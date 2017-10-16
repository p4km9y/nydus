# server mode
```sh
target$ java -jar nydus-1.0-SNAPSHOT.jar --type=server --targetHostPort=localhost:22 --pipeListenerPort=8443
```
# client mode
````sh
target$ java -jar nydus-1.0-SNAPSHOT.jar --type=client --pipeUrl=wss://10.230.18.8:8443/pipe --proxyHostPort=localhost:6666 --proxyUserPwd=user:pwd --forwarderPort=8888
````
# server ssl certificate
### generate
````sh
keytool -genkey -v -alias jetty -keyalg RSA -keysize 2048 -keystore keystore.jks -validity 3650 -providername SUN
````
### put keystore.jks to classpath
# example
```sh
target$ java -jar nydus-1.0-SNAPSHOT.jar --type=server --targetHostPort=localhost:2222 --pipeListenerPort=8443
target$ java -jar nydus-1.0-SNAPSHOT.jar --type=client --pipeUrl=wss://10.230.18.8:8443/pipe --forwarderPort=8888
netcat -lp 2222
netcat localhost 8888
```
