#!/bin/sh
# Copia los recursos de Compose (fuentes) al bundle de la app iOS.
#
# Gradle los genera en shared/build/generated/compose/resourceGenerator/assembledResources/,
# pero este proyecto enlaza un XCFramework pre-construido en vez de usar el flujo estándar
# de CMP (embedAndSignAppleFrameworkForXcode), así que nada los copiaba: la app compilaba
# y luego moría al arrancar con MissingResourceException buscando Roboto-Regular.ttf.
set -e

case "$SDK_NAME" in
  iphoneos*) TASK=assembleIosArm64MainResources ;;
  *) if [ "$(uname -m)" = "arm64" ]; then
       TASK=assembleIosSimulatorArm64MainResources
     else
       TASK=assembleIosX64MainResources
     fi ;;
esac

cd "$SRCROOT/.."
./gradlew --quiet ":shared:$TASK"

SRC=$(find shared/build/generated/compose/resourceGenerator/assembledResources \
  -maxdepth 2 -type d -name composeResources | head -1)

if [ -z "$SRC" ]; then
  echo "error: Gradle no generó los composeResources de iOS (tarea $TASK)"
  exit 1
fi

DEST="$BUILT_PRODUCTS_DIR/$CONTENTS_FOLDER_PATH/compose-resources"
mkdir -p "$DEST"
rsync -a --delete "$SRC" "$DEST/"
echo "compose-resources copiados desde $SRC"
