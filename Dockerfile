# Stage 1: Build the JAR
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run the JAR
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/target/jobtrackerpro-0.0.1-SNAPSHOT.jar jobtrackerpro.jar
EXPOSE 8080

# stage 3: Run the application
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "jobtrackerpro.jar"]