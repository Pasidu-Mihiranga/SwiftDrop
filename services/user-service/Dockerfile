# syntax=docker/dockerfile:1.7
# Build context = repository root (the service needs the shared libs in libs/java):
#   docker build -f services/user-service/Dockerfile -t swiftdrop/user-service .

# ---------- build ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY libs/java libs/java
COPY services services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -U -pl services/user-service -am -DskipTests -Djacoco.skip=true package
# Split the fat jar into layers so dependency layers are cached between builds
RUN java -Djarmode=tools -jar services/user-service/target/user-service.jar extract --layers --destination /extracted

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 swiftdrop && useradd --system --uid 10001 --gid swiftdrop swiftdrop
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "user-service.jar"]
