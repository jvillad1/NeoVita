#!/bin/sh
# Copia los recursos de Compose (fuentes) al bundle de la app iOS.
#
# Gradle los genera en shared/build/generated/compose/resourceGenerator/assembledResources/,
# pero este proyecto enlaza un XCFramework pre-construido en vez de usar el flujo estándar
# de CMP (embedAndSignAppleFrameworkForXcode), así que nada los copiaba: la app compilaba
# y luego moría al arrancar con MissingResourceException buscando Roboto-Regular.ttf.
set -e

# Xcode no hereda el entorno del shell (~/.zshrc): lanzado desde el Dock no tiene JAVA_HOME
# ni java en el PATH, y en esta máquina ningún JDK está donde /usr/libexec/java_home mira.
if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
  for candidate in \
    /usr/local/share/jbrsdk-21/Contents/Home \
    /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  do
    if [ -x "$candidate/bin/java" ]; then
      JAVA_HOME="$candidate"
      export JAVA_HOME
      break
    fi
  done
fi
if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
  echo "error: no se encontró un JDK. Exporta JAVA_HOME o instala un JDK 21 (ver CLAUDE.md)."
  exit 1
fi

# La arquitectura la manda Xcode ($ARCHS), no el host: bajo Rosetta uname -m mentiría.
case "$SDK_NAME" in
  iphoneos*) TASK=assembleIosArm64MainResources ;;
  *) case "$ARCHS" in
       *arm64*) TASK=assembleIosSimulatorArm64MainResources ;;
       *) TASK=assembleIosX64MainResources ;;
     esac ;;
esac

cd "$SRCROOT/.."
./gradlew --quiet ":shared:$TASK"

# El directorio se deriva de la tarea, NO se busca con `find`: assembledResources/ también
# contiene los recursos de wasmJs, y un `find | head -1` puede devolver ésos — hoy funciona
# sólo porque el contenido coincide, y rompería en silencio en cuanto diverjan.
NAME=${TASK#assemble}          # IosX64MainResources
NAME=${NAME%Resources}         # IosX64Main
FIRST=$(printf '%s' "$NAME" | cut -c1 | tr 'A-Z' 'a-z')
REST=$(printf '%s' "$NAME" | cut -c2-)
SRC="shared/build/generated/compose/resourceGenerator/assembledResources/${FIRST}${REST}/composeResources"

if [ ! -d "$SRC" ]; then
  echo "error: la tarea $TASK no dejó recursos en $SRC"
  exit 1
fi

DEST="$BUILT_PRODUCTS_DIR/$CONTENTS_FOLDER_PATH/compose-resources"
mkdir -p "$DEST"
rsync -a --delete "$SRC" "$DEST/"
echo "compose-resources copiados desde $SRC"
