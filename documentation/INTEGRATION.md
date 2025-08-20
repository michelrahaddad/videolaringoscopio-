# INTEGRATION.md

## SDK Integrado

Bibliotecas nativas utilizadas:
- `libffmpeg.so` (vídeo)
- `libGPCam.so` (stream da câmera)
- `libopencv_java3.so` (visão computacional, opcional)

Arquiteturas suportadas:
- arm64-v8a
- armeabi-v7a
- x86
- x86_64

## Pacotes principais
- `com.bsafe.videolaryngoscope.MainActivity` – controla navegação e preview
- `com.bsafe.sdk.*` – contém classes `CamWrapper`, `ffmpegWrapper`, `Native`, etc.

## Permissões necessárias (em `AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

## Ciclo de preview
- `CamWrapper.native_startPreview(surface)` → inicia visualização
- `CamWrapper.native_captureSnapshot(path)` → salva imagem
- `ffmpegWrapper.startRecording(path)` / `stopRecording()` → grava vídeo

## Diretórios
- Imagens e vídeos são salvos em:
  `Android/data/<package>/files/DCIM/Bsafe/`
