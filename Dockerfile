# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 캐시한다. 소스만 바뀌면 의존성은 다시 받지 않는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null

COPY src src
# 테스트는 CI에서 이미 통과한 커밋만 배포하므로 여기서는 건너뛴다.
RUN ./gradlew --no-daemon -q bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다(작은 EC2에서 OOM 방지).
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
