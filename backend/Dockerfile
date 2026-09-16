FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
# -B prints download progress. go-offline is omitted because it pulls extra plugin
# jars and often stalls or fails on interrupted Maven Central transfers.
# -B 会打印下载进度。去掉 go-offline，避免额外插件依赖在弱网下下到一半失败。
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/task-manager-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
