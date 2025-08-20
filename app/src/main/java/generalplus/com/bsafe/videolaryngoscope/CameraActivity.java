package com.bsafe.videolaryngoscope;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.generalplus.ffmpegLib.ffmpegWrapper;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final String FILE_PROVIDER_AUTHORITY = "com.bsafe.videolaryngoscope.provider";

    // Configurações de rede do dispositivo
    private static final String DEVICE_IP = "192.168.100.1";
    private static final int UDP_PORT = 20000;
    private static final int CONNECTION_RETRY_DELAY_MS = 3000;

    // URL do stream - IMPORTANTE: usando RTSP que é o protocolo correto
    private static final String STREAM_URL = "rtsp://192.168.100.1:20000/?action=stream";

    // Componentes da UI
    private GLSurfaceView glSurfaceView;
    private ImageView flashImageView;
    private TextView statusTextView;
    private ImageButton buttonRecord;
    private ImageButton buttonPhoto;
    private LinearLayout shareLayout;

    // SDK Wrapper
    private ffmpegWrapper ffmpeg;

    // Socket UDP para wake-up
    private DatagramSocket udpSocket;

    // Estado da conexão
    private boolean isConnected = false;
    private boolean isRecording = false;
    private String lastMediaPath = null;
    private ExecutorService executorService;

    // Flag para controlar tentativas de reconexão
    private boolean isReconnecting = false;

    /**
     * Handler principal que processa mensagens do ffmpegWrapper
     * Separamos os handlers para evitar conflitos de valores de constantes
     */
    private final Handler ffmpegHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            // Processa apenas mensagens do ffmpegWrapper
            switch (msg.what) {
                case ffmpegWrapper.FFMPEG_STATUS_PLAYING:
                    // Stream iniciado com sucesso
                    if (!isConnected) {
                        isConnected = true;
                        isReconnecting = false;
                        updateStatusText("Status: Conectado");
                        updateButtonStates();
                        Log.i(TAG, "Stream RTSP conectado com sucesso!");
                    }
                    break;

                case ffmpegWrapper.FFMPEG_STATUS_STOPPED:
                    // Stream parou ou foi desconectado
                    if (isConnected) {
                        isConnected = false;
                        updateStatusText("Status: Desconectado");
                        updateButtonStates();
                        Log.w(TAG, "Stream RTSP desconectado");

                        // Tenta reconectar automaticamente se não estiver já tentando
                        if (!isReconnecting) {
                            isReconnecting = true;
                            handler.postDelayed(CameraActivity.this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                        }
                    }
                    break;

                case ffmpegWrapper.FFMPEG_STATUS_BUFFERING:
                    // Stream está carregando/bufferizando
                    updateStatusText("Status: Carregando...");
                    Log.d(TAG, "Stream bufferizando...");
                    break;

                case ffmpegWrapper.FFMPEG_STATUS_SAVESNAPSHOTCOMPLETE:
                    // Foto foi salva com sucesso
                    showFlashEffect();
                    Toast.makeText(CameraActivity.this, "Foto salva!", Toast.LENGTH_SHORT).show();
                    showShareOption();
                    Log.i(TAG, "Snapshot salvo com sucesso");
                    break;

                case ffmpegWrapper.FFMPEG_STATUS_SAVEVIDEOCOMPLETE:
                    // Gravação de vídeo foi concluída
                    isRecording = false;
                    buttonRecord.setImageResource(R.drawable.ic_record);
                    Toast.makeText(CameraActivity.this, "Gravação salva!", Toast.LENGTH_SHORT).show();
                    showShareOption();
                    updateButtonStates();
                    Log.i(TAG, "Gravação de vídeo concluída");
                    break;

                default:
                    // Mensagem desconhecida
                    Log.v(TAG, "Mensagem não tratada do ffmpegWrapper: " + msg.what);
                    break;
            }
        }
    };

    // Handler de conveniência para posts delayed (evita warnings do IDE)
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_camera);
        Log.d(TAG, "onCreate chamado");

        // Inicializa o executor service para tarefas em background
        executorService = Executors.newSingleThreadExecutor();

        // Inicializa as views e configura o SDK
        initViews();
        setupFFmpegWrapper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume chamado");

        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }

        // Inicia a conexão após um pequeno delay para garantir que tudo está inicializado
        handler.postDelayed(this::startStreamWithUDPWakeup, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause chamado");

        // Para o stream quando a activity não está visível
        stopStream();

        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy chamado");

        // Limpa recursos
        closeUdpSocket();

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Inicializa todos os componentes da interface
     */
    private void initViews() {
        // Views principais
        glSurfaceView = findViewById(R.id.gl_surface_view);
        flashImageView = findViewById(R.id.flash_image_view);
        statusTextView = findViewById(R.id.download_status_textview);

        // Botões de controle
        buttonRecord = findViewById(R.id.button_record);
        buttonPhoto = findViewById(R.id.button_photo);
        shareLayout = findViewById(R.id.share_layout);

        // Configura listeners dos botões
        findViewById(R.id.button_back).setOnClickListener(v -> finish());
        buttonRecord.setOnClickListener(v -> toggleRecording());
        buttonPhoto.setOnClickListener(v -> capturePhoto());
        shareLayout.setOnClickListener(v -> shareLastMedia());

        // Estado inicial dos botões
        updateButtonStates();
    }

    /**
     * Configura o FFmpeg wrapper para renderização do stream
     */
    private void setupFFmpegWrapper() {
        try {
            Log.d(TAG, "Configurando FFmpeg wrapper...");

            // Cria e configura o wrapper
            ffmpeg = new ffmpegWrapper();
            ffmpeg.SetViewHandler(ffmpegHandler);

            // Configura o GLSurfaceView para renderização
            glSurfaceView.setEGLContextClientVersion(2);
            glSurfaceView.setRenderer(ffmpeg);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

            Log.d(TAG, "Configuração do FFmpeg concluída");
        } catch (Exception e) {
            Log.e(TAG, "Erro crítico ao configurar o FFmpeg", e);
            Toast.makeText(this, "Erro ao iniciar o player de vídeo", Toast.LENGTH_LONG).show();
            finish(); // Fecha a activity se não conseguir configurar
        }
    }

    /**
     * Método principal que implementa o wake-up UDP seguido da conexão RTSP
     * Este é o fluxo correto: primeiro "acorda" o dispositivo via UDP, depois conecta via RTSP
     */
    private void startStreamWithUDPWakeup() {
        if (isConnected) {
            Log.d(TAG, "Já conectado, ignorando nova tentativa de conexão");
            return;
        }

        updateStatusText("Status: Conectando...");

        // Executa o processo de conexão em background thread
        executorService.execute(() -> {
            Log.d(TAG, "Iniciando processo de wake-up UDP...");

            // Passo 1: Enviar pacote UDP para "acordar" o dispositivo
            if (sendUDPWakeupPacket()) {
                // Pequeno delay para garantir que o dispositivo processou o wake-up
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupção durante o delay pós-wake-up", e);
                }

                // Passo 2: Iniciar o stream RTSP na UI thread
                runOnUiThread(() -> {
                    Log.i(TAG, "Wake-up UDP enviado. Iniciando stream RTSP: " + STREAM_URL);
                    try {
                        // Inicia o stream com opções vazias (usa padrões do ffmpeg)
                        ffmpegWrapper.naInitAndPlay(STREAM_URL, "");
                        Log.d(TAG, "Comando naInitAndPlay enviado com sucesso");
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao iniciar stream RTSP", e);
                        updateStatusText("Erro: Falha ao iniciar stream");

                        // Tenta novamente após um delay
                        isReconnecting = true;
                        handler.postDelayed(this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                    }
                });
            } else {
                runOnUiThread(() -> {
                    updateStatusText("Erro: Falha no wake-up");

                    // Tenta novamente após um delay
                    isReconnecting = true;
                    handler.postDelayed(this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                });
            }
        });
    }

    /**
     * Envia um pacote UDP para "acordar" o dispositivo
     * O dispositivo espera receber um pacote UDP antes de aceitar conexões RTSP
     */
    private boolean sendUDPWakeupPacket() {
        DatagramSocket socket = null;
        try {
            // Cria o socket UDP
            socket = new DatagramSocket();
            socket.setSoTimeout(1000); // Timeout de 1 segundo

            // Prepara o pacote wake-up
            // O conteúdo pode variar dependendo do dispositivo
            // Alguns dispositivos esperam comandos específicos
            byte[] wakeupData = "WAKE_UP".getBytes();
            InetAddress deviceAddress = InetAddress.getByName(DEVICE_IP);
            DatagramPacket packet = new DatagramPacket(
                    wakeupData,
                    wakeupData.length,
                    deviceAddress,
                    UDP_PORT
            );

            // Envia o pacote wake-up
            Log.d(TAG, String.format("Enviando pacote UDP wake-up para %s:%d", DEVICE_IP, UDP_PORT));
            socket.send(packet);

            // Envia múltiplos pacotes para garantir que pelo menos um chegue
            // Alguns dispositivos podem perder o primeiro pacote
            for (int i = 0; i < 3; i++) {
                socket.send(packet);
                Thread.sleep(50); // 50ms entre cada envio
            }

            Log.i(TAG, "Pacotes UDP wake-up enviados com sucesso!");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar pacote UDP wake-up", e);
            return false;
        } finally {
            // Sempre fecha o socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    /**
     * Fecha o socket UDP se estiver aberto
     */
    private void closeUdpSocket() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            try {
                udpSocket.close();
                Log.d(TAG, "Socket UDP fechado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao fechar socket UDP", e);
            }
            udpSocket = null;
        }
    }

    /**
     * Para o stream e limpa recursos
     */
    private void stopStream() {
        if (ffmpeg != null) {
            try {
                ffmpegWrapper.naStop();
                Log.d(TAG, "Stream parado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar stream", e);
            }
        }

        closeUdpSocket();
        isConnected = false;
        isReconnecting = false;
    }

    /**
     * Atualiza o texto de status na UI thread
     */
    private void updateStatusText(final String text) {
        runOnUiThread(() -> {
            if (statusTextView != null) {
                statusTextView.setText(text);
            }
        });
    }

    /**
     * Atualiza o estado dos botões baseado no status da conexão
     */
    private void updateButtonStates() {
        runOnUiThread(() -> {
            // Botões só ficam habilitados quando conectado
            buttonRecord.setEnabled(isConnected);
            buttonPhoto.setEnabled(isConnected && !isRecording);

            // Esconde opção de compartilhar por padrão
            shareLayout.setVisibility(View.INVISIBLE);
        });
    }

    /**
     * Captura uma foto do stream atual
     */
    private void capturePhoto() {
        if (!isConnected || isRecording) {
            Log.w(TAG, "Não pode capturar foto: conectado=" + isConnected + ", gravando=" + isRecording);
            Toast.makeText(this, "Aguarde a conexão ou pare a gravação", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Cria o diretório de imagens se não existir
            File mediaDir = getAppMediaDirectory("Images");
            if (mediaDir == null) {
                Toast.makeText(this, "Erro ao acessar diretório de mídia", Toast.LENGTH_SHORT).show();
                return;
            }

            // Gera nome único para o arquivo
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(mediaDir, "IMG_" + timestamp + ".jpg");
            lastMediaPath = photoFile.getAbsolutePath();

            Log.d(TAG, "Salvando snapshot em: " + lastMediaPath);
            ffmpegWrapper.naSaveSnapshot(lastMediaPath);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao capturar foto", e);
            Toast.makeText(this, "Erro ao capturar foto", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Alterna entre iniciar e parar gravação de vídeo
     */
    private void toggleRecording() {
        if (!isConnected) {
            Log.w(TAG, "Não pode gravar: não conectado");
            Toast.makeText(this, "Aguarde a conexão com o dispositivo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) {
            // Inicia gravação
            try {
                // Cria o diretório de vídeos se não existir
                File mediaDir = getAppMediaDirectory("Videos");
                if (mediaDir == null) {
                    Toast.makeText(this, "Erro ao acessar diretório de mídia", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Gera nome único para o arquivo
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File videoFile = new File(mediaDir, "VID_" + timestamp);
                lastMediaPath = videoFile.getAbsolutePath();

                Log.d(TAG, "Iniciando gravação em: " + lastMediaPath);

                // Inicia a gravação
                if (ffmpegWrapper.naSaveVideo(lastMediaPath) == 0) {
                    isRecording = true;
                    buttonRecord.setImageResource(android.R.drawable.ic_media_pause);
                    Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show();
                    updateButtonStates();
                } else {
                    Toast.makeText(this, "Falha ao iniciar gravação", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar gravação", e);
                Toast.makeText(this, "Erro ao iniciar gravação", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Para gravação
            try {
                ffmpegWrapper.naStopSaveVideo();
                Log.d(TAG, "Parando gravação");
                // O handler receberá FFMPEG_STATUS_SAVEVIDEOCOMPLETE quando terminar
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar gravação", e);
                isRecording = false;
                updateButtonStates();
            }
        }
    }

    /**
     * Obtém o diretório apropriado para salvar mídia
     */
    private File getAppMediaDirectory(String type) {
        // Usa o diretório privado do app para evitar problemas de permissão
        File mediaDir = new File(getExternalFilesDir(null), "BsafeMedia/" + type);

        if (!mediaDir.exists()) {
            if (!mediaDir.mkdirs()) {
                Log.e(TAG, "Não foi possível criar o diretório de mídia: " + mediaDir.getAbsolutePath());
                return null;
            }
        }

        return mediaDir;
    }

    /**
     * Mostra efeito de flash quando tira foto
     */
    private void showFlashEffect() {
        flashImageView.setVisibility(View.VISIBLE);
        flashImageView.setAlpha(0.8f);
        flashImageView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> flashImageView.setVisibility(View.GONE));
    }

    /**
     * Mostra opção de compartilhar após salvar mídia
     */
    private void showShareOption() {
        runOnUiThread(() -> {
            shareLayout.setVisibility(View.VISIBLE);
            shareLayout.setAlpha(0f);
            shareLayout.animate().alpha(1f).setDuration(500);
        });
    }

    /**
     * Compartilha a última mídia capturada
     */
    private void shareLastMedia() {
        if (lastMediaPath == null || lastMediaPath.isEmpty()) {
            Toast.makeText(this, "Nenhuma mídia para compartilhar", Toast.LENGTH_SHORT).show();
            return;
        }

        File fileToShare = new File(lastMediaPath);

        // Verifica se o arquivo existe (vídeos podem ter extensão .mp4 adicionada)
        if (!fileToShare.exists()) {
            File mp4File = new File(lastMediaPath + ".mp4");
            if (mp4File.exists()) {
                fileToShare = mp4File;
            } else {
                Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        try {
            // Cria URI usando FileProvider para compartilhamento seguro
            android.net.Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, fileToShare);
            String mimeType = getContentResolver().getType(uri);

            // Cria intent de compartilhamento
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType != null ? mimeType : "application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Abre o seletor de apps para compartilhamento
            startActivity(Intent.createChooser(shareIntent, "Compartilhar via"));

        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar mídia", e);
            Toast.makeText(this, "Erro ao compartilhar mídia", Toast.LENGTH_SHORT).show();
        }
    }
}