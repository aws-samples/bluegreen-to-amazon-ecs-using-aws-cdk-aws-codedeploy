#During runtime this file will be moved to $PROJECT_HOME/target
FROM public.ecr.aws/amazoncorretto/amazoncorretto:20-al2-jdk
RUN mkdir -p /u01/deploy
WORKDIR /u01/deploy
COPY service-1.0-SNAPSHOT.jar service.jar
#Guest user on alpine linux
USER nobody
HEALTHCHECK CMD curl http://localhost:8080 || exit 1
ENTRYPOINT [ "sh", "-c", "java -jar /u01/deploy/service.jar"]