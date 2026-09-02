# ===========================================================================
# Build
# ===========================================================================
# Reihenfolge mit Absicht: erst pom.xml, dann go-offline, dann src. Eine reine
# Codeaenderung nimmt den Dependency-Layer damit aus dem Cache - der Unterschied
# zwischen ~20 Sekunden und mehreren Minuten pro Deploy.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests


# ===========================================================================
# Laufzeit
# ===========================================================================
# Bewusst KEIN Alpine: OR-Tools liefert native Bibliotheken gegen glibc, unter
# musl bricht der CP-SAT-Solver beim Laden der .so ab - und zwar erst zur
# Laufzeit beim ersten Planungslauf, nicht beim Start.
FROM eclipse-temurin:17-jammy
WORKDIR /app

# Kein root: der Prozess braucht nirgends Schreibrechte ausser in /tmp.
RUN useradd -r -u 1001 spring

COPY --from=build /src/target/*.jar app.jar
USER spring

EXPOSE 8080

# MaxRAMPercentage statt fester Xmx: die JVM richtet sich damit nach dem
# Container-Limit, egal wie viel der Server gerade hergibt.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
