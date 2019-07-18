FROM maven:3-jdk-11
COPY pom.xml /
COPY src /src
RUN mvn clean install
CMD exec mvn exec:java
