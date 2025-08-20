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

// Importa AMBOS os wrappers
import com.generalplus.ffmpegLib.ffmpegWrapper;
import generalplus.com.GPCamLib.CamWrapper;

import java.io.File;
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

    // UI Components
    private GLSurfaceView glSurfaceView;
    private ImageView flashImageView;
    private TextView statusTextView;
    private ImageButton buttonRecord;
    private ImageButton buttonPhoto;
    private LinearLayout shareLayout;

    // Wrappers do SDK
    private ffmpegWrapper ffmpeg;
    private CamWrapper camWrapper; // Adicionado para a tentativa de "despertar"

    private boolean isConnected = false;
    private boolean isRecording = false;
    private String lastMediaPath = null;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case ffmpegWrapper.FFMPEG_STATUS_PLAYING:
                    if (!isConnected) {
                        isConnected = true;
                        updateStatusText("Status: Conectado");
                        updateButtonStates();
                    }
                    break;
                case ffmpegWrapper.FFMPEG_STATUS_STOPPED:
                    if (isConnected) {
                        isConnected = false;
                        updateStatusText("Erro: Conexão perdida");
                        updateButtonStates();
                        handler.postDelayed(CameraActivity.this::startStream, 2000);
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
        initViews();
        setupSDKWrappers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume chamado");
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
        handler.postDelayed(this::startStream, 500);
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

    private void setupSDKWrappers() {
        try {
            Log.d(TAG, "Configurando FFmpeg e CamWrapper...");
            ffmpeg = new ffmpegWrapper();
            ffmpeg.SetViewHandler(handler);
            camWrapper = new CamWrapper(); // Instancia o CamWrapper

            glSurfaceView.setEGLContextClientVersion(2);
            glSurfaceView.setRenderer(ffmpeg);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            Log.d(TAG, "Configuração dos SDKs concluída.");
        } catch (Exception e) {
            Log.e(TAG, "Erro crítico ao configurar os SDKs", e);
            Toast.makeText(this, "Erro ao iniciar o player de vídeo", Toast.LENGTH_LONG).show();
        }
    }

    private void startStream() {
        if (!isConnected) {
            updateStatusText("Status: Conectando...");

            // Plano B: Tenta "despertar" o dispositivo com o CamWrapper primeiro.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                Log.d(TAG, "Tentando conectar com CamWrapper para 'despertar' o dispositivo...");
                // Conecta e imediatamente reinicia o streaming
                if (camWrapper.GPCamConnectToDevice(DEVICE_IP, 0) == 0) {
                    Log.i(TAG, "CamWrapper conectado. Enviando comando de restart stream...");
                    camWrapper.GPCamSendRestartStreaming();
                    camWrapper.GPCamDisconnect(); // Desconecta para liberar a porta para o FFmpeg
                    Log.i(TAG, "Comando de despertar enviado. Procedendo com FFmpeg.");

                    // Agora que o dispositivo foi 'despertado', inicia o player FFmpeg
                    runOnUiThread(() -> {
                        Log.i(TAG, "Iniciando stream RTSP para: " + STREAM_URL);
                        ffmpegWrapper.naInitAndPlay(STREAM_URL, "");
                    });

                } else {
                    Log.w(TAG, "Falha ao conectar com CamWrapper. Tentando FFmpeg diretamente...");
                    // Se a conexão com CamWrapper falhar, tenta o FFmpeg como antes.
                    runOnUiThread(() -> {
                        Log.i(TAG, "Iniciando stream RTSP para: " + STREAM_URL);
                        ffmpegWrapper.naInitAndPlay(STREAM_URL, "");
                    });
                }
            });
        }
    }

    private void stopStream() {
        if (ffmpeg != null) {
            ffmpegWrapper.naStop();
        }
        if (camWrapper != null) {
            camWrapper.GPCamDisconnect();
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
        if (!isConnected || isRecording) return;
        try {
            File mediaDir = getAppMediaDirectory("Images");
            if (mediaDir == null) return;
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(mediaDir, "IMG_" + timestamp + ".jpg");
            lastMediaPath = photoFile.getAbsolutePath();
            Log.d(TAG, "Salvando snapshot em: " + lastMediaPath);
            ffmpegWrapper.naSaveSnapshot(lastMediaPath);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao capturar foto", e);
        }
    }

    private void toggleRecording() {
        if (!isConnected) return;
        if (!isRecording) {
            try {
                File mediaDir = getAppMediaDirectory("Videos");
                if (mediaDir == null) return;
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File videoFile = new File(mediaDir, "VID_" + timestamp);
                lastMediaPath = videoFile.getAbsolutePath();
                if (ffmpegWrapper.naSaveVideo(lastMediaPath) == 0) {
                    isRecording = true;
                    buttonRecord.setImageResource(android.R.drawable.ic_media_pause);
                    Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Falha ao iniciar gravação", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar gravação", e);
            }
        } else {
            ffmpegWrapper.naStopSaveVideo();
        }
        updateButtonStates();
    }

    private File getAppMediaDirectory(String type) {
        File mediaDir = new File(getExternalFilesDir(null), "BsafeMedia/" + type);
        if (!mediaDir.exists()) {
            if (!mediaDir.mkdirs()) {
                Log.e(TAG, "Não foi possível criar o diretório de mídia.");
                return null;
            }
        }
        return mediaDir;
    }

    private void showFlashEffect() {
        flashImageView.setVisibility(View.VISIBLE);
        flashImageView.setAlpha(0.8f);
        flashImageView.animate().alpha(0f).setDuration(300).withEndAction(() -> flashImageView.setVisibility(View.GONE));
    }

    private void showShareOption() {
        runOnUiThread(() -> {
            shareLayout.setVisibility(View.VISIBLE);
            shareLayout.setAlpha(0f);
            shareLayout.animate().alpha(1f).setDuration(500);
        });
    }

    private void shareLastMedia() {
        if (lastMediaPath == null || lastMediaPath.isEmpty()) return;
        File fileToShare = new File(lastMediaPath);
        if (!fileToShare.exists()) {
            File mp4File = new File(lastMediaPath + ".mp4");
            if (mp4File.exists()) fileToShare = mp4File;
            else {
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
        }
    }
}