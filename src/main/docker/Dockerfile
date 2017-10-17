FROM openjdk:8-alpine
MAINTAINER p4km9y

RUN apk add --update bash && rm -rf /var/cache/apk/*

RUN addgroup -S -g 8888 nydus; \
    adduser -S -u 8888 -g nydus nydus

ADD maven/assembly /opt/nydus

RUN nds=`ls /opt/nydus | grep jar` && \
    ln -s /opt/nydus/${nds} /opt/nydus/nydus.jar && \
    chown -R nydus.nydus /opt/nydus

USER nydus

ENTRYPOINT ["java", "-jar", "/opt/nydus/nydus.jar"]
