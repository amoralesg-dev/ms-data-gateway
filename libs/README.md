# Directorio de Drivers JDBC Propietarios

## Progress OpenEdge JDBC Driver

Coloca aquí el archivo JAR del driver JDBC de Progress OpenEdge con el nombre exacto:

```
libs/openedge.jar
```

### ¿Dónde encontrar el JAR?

El driver se encuentra en el directorio de instalación de Progress OpenEdge:

**Windows:**
```
C:\Progress\OpenEdge\java\openedge.jar
```

**Linux:**
```
/usr/dlc/java/openedge.jar
```

O en la instalación de Progress DataDirect JDBC:
```
C:\Program Files\Progress\DataDirect\Connect_for_JDBC_51\lib\openedge.jar
```

### Instalación en repositorio Maven local (alternativa)

Si prefieres instalar el driver en tu repositorio Maven local en lugar de usar `systemPath`:

```bash
mvn install:install-file \
  -Dfile=libs/openedge.jar \
  -DgroupId=com.progress.datadirect \
  -DartifactId=openedge \
  -Dversion=12.0 \
  -Dpackaging=jar
```

Luego cambia en `pom.xml` el scope de `system` a `compile` y elimina el `<systemPath>`.

### Verificación del driver desde DBeaver

Si el driver ya fue validado en DBeaver, busca el JAR en:
- Menú DBeaver: Help > Driver Manager > Progress OpenEdge > Libraries
- El path del JAR estará listado ahí.

### Clase del driver

```
com.ddtek.jdbc.openedge.OpenEdgeDriver
```

### URL de conexión de ejemplo

```
jdbc:datadirect:openedge://hostname:3101;DatabaseName=sports2000
```
