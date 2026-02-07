FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src /app/src
COPY run.sh /app/
RUN mkdir -p bin && \
    javac -d bin -cp src $(find src -name "*.java")

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/bin /app/bin
COPY --from=build /app/src /app/src
CMD ["java", "--add-modules", "jdk.jdi", "-cp", "bin", "visualizer.Server"]
EXPOSE 8080