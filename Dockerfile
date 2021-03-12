FROM amazoncorretto:11-alpine-jdk
COPY build/libs/user-1.0.0-SNAPSHOT.jar user-1.0.0-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/user-1.0.0-SNAPSHOT.jar"]
