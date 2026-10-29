# Estrategia de Dependencias Propietarias y CI/CD: Progress OpenEdge

Este documento analiza los riesgos de depender de artefactos en el repositorio local `~/.m2` y detalla las soluciones técnicas para nuevos desarrolladores, Docker builds y pipelines CI/CD (GitHub Actions, Jenkins).

---

## 1. Diagnóstico del Problema y Riesgos de `~/.m2`

El driver oficial de Progress OpenEdge (`openedge.jar` y `progress.jar`) no está alojado en Maven Central ni en ningún repositorio público debido a licenciamiento propietario.

### Riesgos identificados al depender de instalación manual en `~/.m2`:
1. **Entorno de Nuevo Desarrollador:** Si un desarrollador clona el repositorio e intenta compilar directamente (`mvn compile`), el build fallará inmediatamente si los JARs no están instalados previamente en su `~/.m2`.
2. **Pipelines de CI/CD Efímeros (GitHub Actions / Kubernetes Runners):** En runners limpios donde no existe caché de `~/.m2`, la compilación fallará por dependencias no resueltas.
3. **Docker Multi-stage Builds:** Si un `Dockerfile` compila el proyecto desde cero con Maven en un contenedor limpio, fallará al no tener acceso al `~/.m2` del host.

---

## 2. Soluciones Implementadas y Recomendadas

### Solución A: Repositorio Maven Local Embebido en el Proyecto (Implementable sin Infraestructura Externa)
El proyecto incluye los JARs dentro del repositorio de código (por ejemplo en `libs/repo` o scripts automáticos).
Se puede configurar un repositorio local relativo en el `pom.xml`:

```xml
<repositories>
    <repository>
        <id>project-local-repo</id>
        <url>file://${project.basedir}/libs/repo</url>
    </repository>
</repositories>
```

Con esta configuración, tanto Maven en local, Docker y cualquier runner CI/CD resolverán las dependencias directamente desde el árbol del proyecto sin pasos manuales previos.

---

### Solución B: Script de Inicialización Automatizado (`install-drivers.ps1` / `install-drivers.sh`)
El proyecto ya cuenta con `install-drivers.ps1`, el cual extrae los binarios de `java.zip` e instala los artefactos en `~/.m2` automáticamente:
```powershell
.\install-drivers.ps1
```

Para entornos Linux / MacOS / CI/CD basados en bash, se provee el script equivalente:
```bash
mvn install:install-file \
  -Dfile=libs/openedge.jar \
  -DgroupId=com.progress.datadirect \
  -DartifactId=openedge \
  -Dversion=6.0.0 \
  -Dpackaging=jar \
  -DgeneratePom=true

mvn install:install-file \
  -Dfile=libs/progress.jar \
  -DgroupId=com.progress \
  -DartifactId=progress \
  -Dversion=12.0.0 \
  -Dpackaging=jar \
  -DgeneratePom=true
```

---

### Solución C: Repositorio Maven Corporativo (Nexus / JFrog Artifactory) - *Recomendación Definitiva*
Para entornos empresariales, los JARs deben subirse una única vez al gestor de artefactos interno:

```xml
<!-- En ~/.m2/settings.xml o pom.xml -->
<repositories>
    <repository>
        <id>corporate-releases</id>
        <url>https://nexus.miempresa.com/repository/maven-releases/</url>
    </repository>
</repositories>
```

---

## 3. Guías de Integración para Pipelines

### A. GitHub Actions (`.github/workflows/build.yml`)
```yaml
name: Build & Test MS Data Gateway

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Código
        uses: actions/checkout@v4

      - name: Configurar Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Descomprimir e Instalar Drivers Propietarios
        run: |
          unzip -o java.zip -d libs/
          mvn install:install-file -Dfile=libs/openedge.jar -DgroupId=com.progress.datadirect -DartifactId=openedge -Dversion=6.0.0 -Dpackaging=jar -DgeneratePom=true
          mvn install:install-file -Dfile=libs/progress.jar -DgroupId=com.progress -DartifactId=progress -Dversion=12.0.0 -Dpackaging=jar -DgeneratePom=true

      - name: Compilar y Ejecutar Pruebas
        run: mvn clean verify
```

### B. Jenkins Pipeline (`Jenkinsfile`)
```groovy
pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-21'
        }
    }
    stages {
        stage('Instalar Drivers OpenEdge') {
            steps {
                sh '''
                    unzip -o java.zip -d libs/
                    mvn install:install-file -Dfile=libs/openedge.jar -DgroupId=com.progress.datadirect -DartifactId=openedge -Dversion=6.0.0 -Dpackaging=jar -DgeneratePom=true
                    mvn install:install-file -Dfile=libs/progress.jar -DgroupId=com.progress -DartifactId=progress -Dversion=12.0.0 -Dpackaging=jar -DgeneratePom=true
                '''
            }
        }
        stage('Build & Test') {
            steps {
                sh 'mvn clean package'
            }
        }
    }
}
```

### C. Docker Build Limpio (Multi-stage)
Para compilar y empaquetar dentro de un contenedor Docker sin requerir que el host tenga Maven ni los drivers preinstalados:

```dockerfile
# Etapa 1: Build y resolución de dependencias
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copiar archivos fuente y ZIP de dependencias
COPY pom.xml .
COPY java.zip .
COPY src ./src

# Instalar drivers propietarios en el .m2 efímero del build container
RUN unzip java.zip -d libs/ && \
    mvn install:install-file -Dfile=libs/openedge.jar -DgroupId=com.progress.datadirect -DartifactId=openedge -Dversion=6.0.0 -Dpackaging=jar -DgeneratePom=true && \
    mvn install:install-file -Dfile=libs/progress.jar -DgroupId=com.progress -DartifactId=progress -Dversion=12.0.0 -Dpackaging=jar -DgeneratePom=true

# Compilar y empaquetar
RUN mvn clean package -DskipTests

# Etapa 2: Runtime ligero
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/target/ms-data-gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
