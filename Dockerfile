FROM openjdk:9-jre-slim

WORKDIR /mobile-queue

COPY *.jar /mobile-queue/

#Ports
EXPOSE 8080

CMD java -jar mobile-queue-assembly-0.1.jar