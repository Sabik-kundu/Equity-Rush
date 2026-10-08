# Equity Rush: builds from source, runs on Railway (or any Docker host)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out && javac -encoding UTF-8 -d out src/bullrun/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY web ./web
# Save folder. On Railway attach a Volume mounted at /data so the game survives redeploys.
ENV BULLRUN_DATA=/data
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
EXPOSE 8080
CMD ["java", "-cp", "out", "bullrun.Main"]
