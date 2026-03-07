#!/bin/bash

# Script de compilación con Docker para CleanSky Contactless
# Uso: ./build-docker.sh [--test-only]

set -e

IMAGE_NAME="cleansky-contactless-builder"
CONTAINER_NAME="cleansky-build"
OUTPUT_DIR="./build-output"
TEST_ONLY=false

if [ "$1" = "--test-only" ]; then
    TEST_ONLY=true
fi

echo "=== CleanSky Contactless - Docker Build ==="

# Crear directorio de salida
mkdir -p "$OUTPUT_DIR"

# Construir imagen Docker (capas cacheadas si solo cambió código fuente)
echo "[1/3] Construyendo imagen Docker..."
docker build -t "$IMAGE_NAME" .

if [ "$TEST_ONLY" = true ]; then
    echo "[2/3] Ejecutando tests..."
    docker run --rm \
        --name "$CONTAINER_NAME" \
        -v cleansky-gradle-cache:/root/.gradle \
        "$IMAGE_NAME" \
        ./gradlew testDebugUnitTest --no-daemon --build-cache
    echo ""
    echo "=== Tests exitosos ==="
    exit 0
fi

# Ejecutar contenedor y compilar
echo "[2/3] Compilando APK..."
docker run --rm \
    --name "$CONTAINER_NAME" \
    -v "$(pwd)/$OUTPUT_DIR:/out" \
    -v cleansky-gradle-cache:/root/.gradle \
    "$IMAGE_NAME"

# Verificar resultado
if [ -f "$OUTPUT_DIR/app-debug.apk" ]; then
    echo ""
    echo "=== Build exitoso ==="
    echo "APK: $OUTPUT_DIR/app-debug.apk"
    ls -lh "$OUTPUT_DIR/app-debug.apk"
else
    echo ""
    echo "=== Error: APK no encontrado ==="
    exit 1
fi
