FROM openjdk:8-jdk

ENV sbtVersion 0.13.15

RUN \
  curl -L -o sbt-${sbtVersion}.deb http://dl.bintray.com/sbt/debian/sbt-${sbtVersion}.deb && \
  dpkg -i sbt-${sbtVersion}.deb && \
  rm sbt-${sbtVersion}.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion
  
RUN git clone https://github.com/luechtdiode/mobile-queue.git; \
  cd /mobile-queue;\
  sbt compile; \
  sbt test; \

#Ports
EXPOSE 8080

CMD sbt run