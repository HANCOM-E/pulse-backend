# ---- build stage ----
# 프로젝트 gradle 래퍼(9.5.1)를 그대로 사용해 버전 일치. JDK 21 고정.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar -x test --no-daemon

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/pulse-backend-0.0.1-SNAPSHOT.jar app.jar
# 포트는 앱이 $PORT(server.port)로 바인딩. Render가 주입.
ENTRYPOINT ["java", "-jar", "app.jar"]
