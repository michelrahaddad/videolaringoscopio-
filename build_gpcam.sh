#!/bin/bash

# Script para compilar libgpcam.so usando NDK

echo "=== Building libgpcam.so ==="

# Criar diretório jni se não existir
mkdir -p app/src/main/jni

# Copiar arquivos para o diretório jni
echo "Copiando arquivos para jni..."
cp gpcam_jni_stub.cpp app/src/main/jni/
cp Android.mk app/src/main/jni/
cp Application.mk app/src/main/jni/
cp generalplus_com_GPCamLib_CamWrapper.h app/src/main/jni/

# Entrar no diretório do projeto
cd app/src/main

# Verificar se NDK está configurado
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "Erro: ANDROID_NDK_HOME não está configurado!"
    echo "Configure com: export ANDROID_NDK_HOME=/caminho/para/android-ndk"
    exit 1
fi

# Compilar usando ndk-build
echo "Compilando com ndk-build..."
$ANDROID_NDK_HOME/ndk-build

# Verificar se a compilação foi bem sucedida
if [ $? -eq 0 ]; then
    echo "Compilação bem sucedida!"
    
    # Copiar as bibliotecas geradas para jniLibs
    echo "Copiando bibliotecas para jniLibs..."
    mkdir -p jniLibs
    cp -r libs/* jniLibs/
    
    # Limpar arquivos temporários
    rm -rf obj
    rm -rf libs
    
    echo "=== Build completo! ==="
    echo "Bibliotecas disponíveis em:"
    ls -la jniLibs/*/libgpcam.so
else
    echo "Erro na compilação!"
    exit 1
fi