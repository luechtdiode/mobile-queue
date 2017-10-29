FROM openjdk:8-jdk

RUN \
  curl -L -o sbt-0.13.15.deb http://dl.bintray.com/sbt/debian/sbt-0.13.15.deb && \
  dpkg -i sbt-0.13.15.deb && \
  rm sbt-0.13.15.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion
  
RUN git clone https://github.com/luechtdiode/mobile-queue.git; \
  cd /mobile-queue;\
  sbt compile; \
  sbt test; \

WORKDIR /mobile-queue

#Ports
EXPOSE 8080

CMD sbt run