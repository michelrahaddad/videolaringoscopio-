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
    private static final String STREAM_URL = "rtsp://192.168.100.1:20000/?action=stream";
    private static final String DEVICE_IP = "192.168.100.1";
    private static final int UDP_PORT = 20000;
    private static final int CONNECTION_RETRY_DELAY_MS = 3000;

    // UI Components
    private GLSurfaceView glSurfaceView;
    private ImageView flashImageView;
    private TextView statusTextView;
    private ImageButton buttonRecord;
    private ImageButton buttonPhoto;
    private LinearLayout shareLayout;

    // SDK Wrapper
    private ffmpegWrapper ffmpeg;

    // Connection state
    private boolean isConnected = false;
    private boolean isRecording = false;
    private String lastMediaPath = null;
    private ExecutorService executorService;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case ffmpegWrapper.FFMPEG_STATUS_PLAYING:
                    if (!isConnected) {
                        isConnected = true;
                        updateStatusText("Status: Conectado");
                        updateButtonStates();
                        Log.i(TAG, "Stream conectado com sucesso!");
                    }
                    break;
                case ffmpegWrapper.FFMPEG_STATUS_STOPPED:
                    if (isConnected) {
                        isConnected = false;
                        updateStatusText("Erro: Conexão perdida");
                        updateButtonStates();
                        Log.w(TAG, "Stream desconectado. Tentando reconectar...");
                        handler.postDelayed(CameraActivity.this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                    }
                    break;
                case ffmpegWrapper.FFMPEG_STATUS_SAVESNAPSHOTCOMPLETE:
                    showFlashEffect();
                    Toast.makeText(CameraActivity.this, "Foto salva!", Toast.LENGTH_SHORT).show();
                    showShareOption();
                    break;
                case ffmpegWrapper.FFMPEG_STATUS_SAVEVIDEOCOMPLETE:
                    isRecording = false;
                    buttonRecord.setImageResource(R.drawable.ic_record);
                    Toast.makeText(CameraActivity.this, "Gravação salva!", Toast.LENGTH_SHORT).show();
                    showShareOption();
                    updateButtonStates();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_camera);
        Log.d(TAG, "onCreate chamado");

        executorService = Executors.newSingleThreadExecutor();
        initViews();
        setupSDKWrapper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume chamado");
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        // Inicia a conexão após um pequeno delay
        handler.postDelayed(this::startStreamWithUDPWakeup, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause chamado");
        stopStream();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void initViews() {
        glSurfaceView = findViewById(R.id.gl_surface_view);
        flashImageView = findViewById(R.id.flash_image_view);
        statusTextView = findViewById(R.id.download_status_textview);
        buttonRecord = findViewById(R.id.button_record);
        buttonPhoto = findViewById(R.id.button_photo);
        shareLayout = findViewById(R.id.share_layout);

        findViewById(R.id.button_back).setOnClickListener(v -> finish());
        buttonRecord.setOnClickListener(v -> toggleRecording());
        buttonPhoto.setOnClickListener(v -> capturePhoto());
        shareLayout.setOnClickListener(v -> shareLastMedia());

        updateButtonStates();
    }

    private void setupSDKWrapper() {
        try {
            Log.d(TAG, "Configurando FFmpeg wrapper...");
            ffmpeg = new ffmpegWrapper();
            ffmpeg.SetViewHandler(handler);

            glSurfaceView.setEGLContextClientVersion(2);
            glSurfaceView.setRenderer(ffmpeg);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

            Log.d(TAG, "Configuração do FFmpeg concluída.");
        } catch (Exception e) {
            Log.e(TAG, "Erro crítico ao configurar o FFmpeg", e);
            Toast.makeText(this, "Erro ao iniciar o player de vídeo", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Método principal que implementa o wake-up UDP antes de iniciar o stream RTSP
     */
    private void startStreamWithUDPWakeup() {
        if (isConnected) {
            Log.d(TAG, "Já conectado, ignorando nova tentativa de conexão");
            return;
        }

        updateStatusText("Status: Conectando...");

        // Executa o wake-up UDP em background thread
        executorService.execute(() -> {
            Log.d(TAG, "Iniciando processo de wake-up UDP...");

            if (sendUDPWakeupPacket()) {
                // Pequeno delay para garantir que o dispositivo processou o wake-up
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupção durante o delay pós-wake-up", e);
                }

                // Inicia o stream RTSP na UI thread
                runOnUiThread(() -> {
                    Log.i(TAG, "Wake-up UDP enviado. Iniciando stream RTSP: " + STREAM_URL);
                    try {
                        ffmpegWrapper.naInitAndPlay(STREAM_URL, "");
                        Log.d(TAG, "Comando naInitAndPlay enviado com sucesso");
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao iniciar stream RTSP", e);
                        updateStatusText("Erro: Falha ao iniciar stream");
                        // Retry após um delay
                        handler.postDelayed(this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                    }
                });
            } else {
                runOnUiThread(() -> {
                    updateStatusText("Erro: Falha no wake-up");
                    // Retry após um delay
                    handler.postDelayed(this::startStreamWithUDPWakeup, CONNECTION_RETRY_DELAY_MS);
                });
            }
        });
    }

    /**
     * Envia um pacote UDP para "acordar" o dispositivo
     * Simula o comportamento do app funcionante: envia dados UDP para 192.168.100.1:20000
     */
    private boolean sendUDPWakeupPacket() {
        DatagramSocket socket = null;
        try {
            // Cria o socket UDP
            socket = new DatagramSocket();
            socket.setSoTimeout(1000); // Timeout de 1 segundo

            // Prepara o pacote wake-up
            // Pode ser qualquer dado pequeno, o importante é "bater na porta" do dispositivo
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

            // Tenta enviar múltiplos pacotes para garantir que pelo menos um chegue
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
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void stopStream() {
        if (ffmpeg != null) {
            try {
                ffmpegWrapper.naStop();
                Log.d(TAG, "Stream parado");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar stream", e);
            }
        }
        isConnected = false;
    }

    private void updateStatusText(final String text) {
        runOnUiThread(() -> {
            if (statusTextView != null) {
                statusTextView.setText(text);
            }
        });
    }

    private void updateButtonStates() {
        runOnUiThread(() -> {
            buttonRecord.setEnabled(isConnected);
            buttonPhoto.setEnabled(isConnected && !isRecording);
            shareLayout.setVisibility(View.INVISIBLE);
        });
    }

    private void capturePhoto() {
        if (!isConnected || isRecording) {
            Log.w(TAG, "Não pode capturar foto: conectado=" + isConnected + ", gravando=" + isRecording);
            return;
        }

        try {
            File mediaDir = getAppMediaDirectory("Images");
            if (mediaDir == null) {
                Toast.makeText(this, "Erro ao acessar diretório de mídia", Toast.LENGTH_SHORT).show();
                return;
            }

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

    private void toggleRecording() {
        if (!isConnected) {
            Log.w(TAG, "Não pode gravar: não conectado");
            return;
        }

        if (!isRecording) {
            try {
                File mediaDir = getAppMediaDirectory("Videos");
                if (mediaDir == null) {
                    Toast.makeText(this, "Erro ao acessar diretório de mídia", Toast.LENGTH_SHORT).show();
                    return;
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File videoFile = new File(mediaDir, "VID_" + timestamp);
                lastMediaPath = videoFile.getAbsolutePath();

                Log.d(TAG, "Iniciando gravação em: " + lastMediaPath);
                if (ffmpegWrapper.naSaveVideo(lastMediaPath) == 0) {
                    isRecording = true;
                    buttonRecord.setImageResource(android.R.drawable.ic_media_pause);
                    Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Falha ao iniciar gravação", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar gravação", e);
                Toast.makeText(this, "Erro ao iniciar gravação", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                ffmpegWrapper.naStopSaveVideo();
                Log.d(TAG, "Parando gravação");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar gravação", e);
            }
        }
        updateButtonStates();
    }

    private File getAppMediaDirectory(String type) {
        File mediaDir = new File(getExternalFilesDir(null), "BsafeMedia/" + type);
        if (!mediaDir.exists()) {
            if (!mediaDir.mkdirs()) {
                Log.e(TAG, "Não foi possível criar o diretório de mídia: " + mediaDir.getAbsolutePath());
                return null;
            }
        }
        return mediaDir;
    }

    private void showFlashEffect() {
        flashImageView.setVisibility(View.VISIBLE);
        flashImageView.setAlpha(0.8f);
        flashImageView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> flashImageView.setVisibility(View.GONE));
    }

    private void showShareOption() {
        runOnUiThread(() -> {
            shareLayout.setVisibility(View.VISIBLE);
            shareLayout.setAlpha(0f);
            shareLayout.animate().alpha(1f).setDuration(500);
        });
    }

    private void shareLastMedia() {
        if (lastMediaPath == null || lastMediaPath.isEmpty()) {
            Toast.makeText(this, "Nenhuma mídia para compartilhar", Toast.LENGTH_SHORT).show();
            return;
        }

        File fileToShare = new File(lastMediaPath);

        // Verifica se o arquivo existe (pode ter sido salvo com extensão .mp4)
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
            android.net.Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, fileToShare);
            String mimeType = getContentResolver().getType(uri);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType != null ? mimeType : "application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Compartilhar via"));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar mídia", e);
            Toast.makeText(this, "Erro ao compartilhar mídia", Toast.LENGTH_SHORT).show();
        }
    }
}