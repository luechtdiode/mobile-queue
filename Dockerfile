FROM openjdk:11-jre-slim

WORKDIR /mobile-queue

COPY target/scala-2.12/*.jar /mobile-queue/app.jar

#Ports
EXPOSE 8080

CMD java -jar app.jar