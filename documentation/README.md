# B-safe Videolaryngoscope App

Este aplicativo Android foi desenvolvido para operar com o hardware de videolaringoscópio da Simple Health Solutions utilizando o SDK nativo `CVGoPlusDrone` + `ShareLibrary`.

## Funcionalidades
- Pré-visualização em tempo real da câmera do laringoscópio
- Captura de imagem JPEG
- Gravação de vídeo MP4
- Zoom digital
- Rotação de tela (0°/90°/180°/270°)
- Compartilhamento de mídia

## Tecnologias
- Android SDK
- JNI com bibliotecas nativas `.so`
- `CamWrapper` para preview, snapshot e zoom
- `ffmpegWrapper` para gravação de vídeo
- Interface moderna (Material Design)
