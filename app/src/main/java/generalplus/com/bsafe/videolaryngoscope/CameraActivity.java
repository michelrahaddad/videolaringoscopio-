package com.bsafe.videolaryngoscope;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

    // Configurações de rede baseadas na análise do protocolo
    private static final String DEVICE_IP = "192.168.100.1";
    private static final int CONTROL_PORT = 20000;  // Porta UDP para comandos
    private static final int DATA_PORT = 10900;      // Porta UDP para receber vídeo

    // Comandos JHCMD do protocolo proprietário
    private static final byte[] CMD_HANDSHAKE_1 = {'J', 'H', 'C', 'M', 'D', 0x10, 0x00};
    private static final byte[] CMD_HANDSHAKE_2 = {'J', 'H', 'C', 'M', 'D', 0x20, 0x00};
    private static final byte[] CMD_START_STREAM = {'J', 'H', 'C', 'M', 'D', (byte)0xD0, 0x01};
    private static final byte[] CMD_STOP_STREAM = {'J', 'H', 'C', 'M', 'D', (byte)0xD0, 0x02};

    private static final int KEEPALIVE_INTERVAL_MS = 5000; // 5 segundos

    // Componentes da UI
    private ImageView streamImageView;
    private ImageView flashImageView;
    private TextView statusTextView;
    private ImageButton buttonRecord;
    private ImageButton buttonPhoto;
    private LinearLayout shareLayout;

    // Sockets UDP
    private DatagramSocket controlSocket;
    private DatagramSocket dataSocket;

    // Estado
    private volatile boolean isConnected = false;
    private volatile boolean isReceivingStream = false;
    private boolean isRecording = false;
    private String lastMediaPath = null;

    // Threads
    private ExecutorService executorService;
    private Thread streamReceiverThread;
    private Thread keepAliveThread;

    // Buffer para frames
    private ByteArrayOutputStream currentFrameData;
    private int lastFrameCounter = -1;
    private int packetsInCurrentFrame = 0;

    // Para gravação
    private FileOutputStream videoOutputStream;
    private File currentVideoFile;
    private int framesRecorded = 0;

    // Para captura de foto
    private Bitmap lastFrameBitmap = null;

    // Handler para UI
    private final Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 1: // Conectado
                    isConnected = true;
                    updateStatusText("Status: Conectado");
                    updateButtonStates();
                    break;

                case 2: // Desconectado
                    isConnected = false;
                    updateStatusText("Status: Desconectado");
                    updateButtonStates();
                    reconnectAfterDelay();
                    break;

                case 3: // Frame recebido
                    byte[] jpegData = (byte[]) msg.obj;
                    displayFrame(jpegData);
                    if (isRecording) {
                        saveFrameToVideo(jpegData);
                    }
                    break;

                case 4: // Erro
                    String error = (String) msg.obj;
                    updateStatusText("Erro: " + error);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_camera);
        Log.d(TAG, "onCreate - Iniciando CameraActivity");

        executorService = Executors.newCachedThreadPool();
        currentFrameData = new ByteArrayOutputStream(100 * 1024); // 100KB inicial

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - Conectando ao dispositivo");

        // Inicia conexão após pequeno delay
        uiHandler.postDelayed(() -> {
            executorService.execute(this::connectToDevice);
        }, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause - Desconectando");

        if (isRecording) {
            stopRecording();
        }

        disconnectFromDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void initViews() {
        // Localiza views
        streamImageView = findViewById(R.id.jpeg_image_view);
        flashImageView = findViewById(R.id.flash_image_view);
        statusTextView = findViewById(R.id.download_status_textview);
        buttonRecord = findViewById(R.id.button_record);
        buttonPhoto = findViewById(R.id.button_photo);
        shareLayout = findViewById(R.id.share_layout);

        // Configuração inicial
        streamImageView.setVisibility(View.VISIBLE);
        findViewById(R.id.gl_surface_view).setVisibility(View.GONE); // Não usa OpenGL

        // Listeners
        findViewById(R.id.button_back).setOnClickListener(v -> finish());
        buttonRecord.setOnClickListener(v -> toggleRecording());
        buttonPhoto.setOnClickListener(v -> capturePhoto());
        shareLayout.setOnClickListener(v -> shareLastMedia());

        updateButtonStates();
    }

    /**
     * Conecta ao dispositivo usando protocolo JHCMD
     */
    private void connectToDevice() {
        try {
            Log.d(TAG, ">>> INICIANDO CONEXÃO COM CÂMERA <<<");
            updateStatusText("Conectando...");

            // Cria sockets
            controlSocket = new DatagramSocket();
            controlSocket.setSoTimeout(3000);

            dataSocket = new DatagramSocket(DATA_PORT);
            dataSocket.setSoTimeout(100); // Timeout curto

            Log.d(TAG, "Sockets criados - Porta de dados: " + DATA_PORT);

            InetAddress deviceAddress = InetAddress.getByName(DEVICE_IP);

            // Sequência de inicialização
            Log.d(TAG, "Enviando handshake...");

            sendCommand(CMD_HANDSHAKE_1, deviceAddress);
            Thread.sleep(200);

            sendCommand(CMD_HANDSHAKE_2, deviceAddress);
            Thread.sleep(200);

            sendCommand(CMD_START_STREAM, deviceAddress);
            Thread.sleep(100);
            sendCommand(CMD_START_STREAM, deviceAddress); // Envia 2x

            // Inicia threads
            isReceivingStream = true;
            startStreamReceiver();
            startKeepAlive(deviceAddress);

            uiHandler.sendEmptyMessage(1);
            Log.d(TAG, ">>> CONEXÃO ESTABELECIDA <<<");

        } catch (Exception e) {
            Log.e(TAG, "ERRO ao conectar", e);
            Message msg = uiHandler.obtainMessage(4, "Falha na conexão: " + e.getMessage());
            uiHandler.sendMessage(msg);
            disconnectFromDevice();
        }
    }

    private void sendCommand(byte[] command, InetAddress address) throws Exception {
        DatagramPacket packet = new DatagramPacket(
                command, command.length, address, CONTROL_PORT
        );
        controlSocket.send(packet);

        // Log para debug
        StringBuilder hex = new StringBuilder("CMD enviado: ");
        for (byte b : command) {
            hex.append(String.format("%02X ", b & 0xFF));
        }
        Log.d(TAG, hex.toString());
    }

    /**
     * Thread receptora de stream
     */
    private void startStreamReceiver() {
        streamReceiverThread = new Thread(() -> {
            byte[] buffer = new byte[1500];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            Log.d(TAG, "Receptor iniciado na porta " + DATA_PORT);

            int totalPackets = 0;
            long startTime = System.currentTimeMillis();

            while (isReceivingStream) {
                try {
                    dataSocket.receive(packet);
                    totalPackets++;

                    // Log periódico
                    if (totalPackets % 100 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        float rate = (totalPackets * 1000f) / elapsed;
                        Log.d(TAG, String.format("Pacotes: %d (%.0f/s)", totalPackets, rate));
                    }

                    // Processa pacote
                    processPacket(packet.getData(), packet.getLength());

                } catch (Exception e) {
                    // Timeout é normal, outros erros não
                    if (!e.getMessage().contains("timeout") && isReceivingStream) {
                        Log.e(TAG, "Erro recebendo", e);
                    }
                }
            }

            Log.d(TAG, "Receptor finalizado. Total: " + totalPackets + " pacotes");
        });

        streamReceiverThread.setName("StreamRX");
        streamReceiverThread.start();
    }

    /**
     * Processa pacote de vídeo
     */
    private void processPacket(byte[] data, int length) {
        if (length <= 8) return; // Muito pequeno

        // Header de 8 bytes
        int frameNum = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int packetIdx = data[3] & 0xFF;

        // Novo frame?
        if (frameNum != lastFrameCounter || packetIdx == 0) {
            if (currentFrameData.size() > 0) {
                checkAndSendFrame();
            }
            lastFrameCounter = frameNum;
            currentFrameData.reset();
            packetsInCurrentFrame = 0;
        }

        // Adiciona dados (pula header)
        currentFrameData.write(data, 8, length - 8);
        packetsInCurrentFrame++;

        // Verifica fim do JPEG (FFD9)
        if (length >= 10) {
            int p = length - 2;
            if ((data[p] & 0xFF) == 0xFF && (data[p + 1] & 0xFF) == 0xD9) {
                checkAndSendFrame();
            }
        }
    }

    /**
     * Verifica e envia frame completo
     */
    private void checkAndSendFrame() {
        byte[] jpeg = currentFrameData.toByteArray();

        // Valida JPEG (começa com FFD8)
        if (jpeg.length > 2 &&
                (jpeg[0] & 0xFF) == 0xFF &&
                (jpeg[1] & 0xFF) == 0xD8) {

            Message msg = uiHandler.obtainMessage(3, jpeg);
            uiHandler.sendMessage(msg);
        }

        currentFrameData.reset();
        packetsInCurrentFrame = 0;
    }

    /**
     * Thread de keep-alive
     */
    private void startKeepAlive(InetAddress address) {
        keepAliveThread = new Thread(() -> {
            Log.d(TAG, "Keep-alive iniciado");
            int count = 0;

            while (isReceivingStream) {
                try {
                    Thread.sleep(KEEPALIVE_INTERVAL_MS);
                    sendCommand(CMD_START_STREAM, address);

                    if (++count % 10 == 0) {
                        Log.d(TAG, "Keep-alive #" + count);
                    }
                } catch (Exception e) {
                    if (isReceivingStream) {
                        Log.e(TAG, "Erro keep-alive", e);
                    }
                }
            }
        });

        keepAliveThread.setName("KeepAlive");
        keepAliveThread.start();
    }

    /**
     * Desconecta do dispositivo
     */
    private void disconnectFromDevice() {
        Log.d(TAG, ">>> DESCONECTANDO <<<");
        isReceivingStream = false;

        // Envia comando stop
        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                sendCommand(CMD_STOP_STREAM, InetAddress.getByName(DEVICE_IP));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro enviando STOP", e);
        }

        // Fecha sockets
        if (controlSocket != null) controlSocket.close();
        if (dataSocket != null) dataSocket.close();

        // Aguarda threads
        try {
            if (streamReceiverThread != null) {
                streamReceiverThread.join(2000);
            }
            if (keepAliveThread != null) {
                keepAliveThread.join(1000);
            }
        } catch (InterruptedException ignored) {}

        uiHandler.sendEmptyMessage(2);
    }

    /**
     * Reconecta após delay
     */
    private void reconnectAfterDelay() {
        uiHandler.postDelayed(() -> {
            if (!isConnected && !isFinishing()) {
                executorService.execute(this::connectToDevice);
            }
        }, 3000);
    }

    /**
     * Exibe frame na tela
     */
    private void displayFrame(byte[] jpegData) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            if (bitmap != null) {
                lastFrameBitmap = bitmap;
                runOnUiThread(() -> streamImageView.setImageBitmap(bitmap));
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro exibindo frame", e);
        }
    }

    /**
     * Captura foto
     */
    private void capturePhoto() {
        if (!isConnected || lastFrameBitmap == null) {
            Toast.makeText(this, "Aguarde conexão", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File photoDir = new File(getExternalFilesDir(null), "BsafeMedia/Images");
            if (!photoDir.exists()) photoDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File photoFile = new File(photoDir, "IMG_" + timestamp + ".jpg");

            FileOutputStream out = new FileOutputStream(photoFile);
            lastFrameBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();

            lastMediaPath = photoFile.getAbsolutePath();

            showFlashEffect();
            Toast.makeText(this, "Foto salva!", Toast.LENGTH_SHORT).show();
            showShareOption();

        } catch (Exception e) {
            Log.e(TAG, "Erro salvando foto", e);
            Toast.makeText(this, "Erro ao salvar", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Alterna gravação
     */
    private void toggleRecording() {
        if (!isConnected) {
            Toast.makeText(this, "Aguarde conexão", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            File videoDir = new File(getExternalFilesDir(null), "BsafeMedia/Videos");
            if (!videoDir.exists()) videoDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            currentVideoFile = new File(videoDir, "VID_" + timestamp + ".mjpeg");

            videoOutputStream = new FileOutputStream(currentVideoFile);
            framesRecorded = 0;
            isRecording = true;

            buttonRecord.setImageResource(android.R.drawable.ic_media_pause);
            Toast.makeText(this, "Gravando...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Erro iniciando gravação", e);
        }
    }

    private void stopRecording() {
        isRecording = false;

        try {
            if (videoOutputStream != null) {
                videoOutputStream.close();
                videoOutputStream = null;
            }

            if (currentVideoFile != null) {
                lastMediaPath = currentVideoFile.getAbsolutePath();
                buttonRecord.setImageResource(R.drawable.ic_record);
                Toast.makeText(this, "Gravação salva! (" + framesRecorded + " frames)",
                        Toast.LENGTH_SHORT).show();
                showShareOption();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro parando gravação", e);
        }
    }

    private void saveFrameToVideo(byte[] jpegData) {
        if (videoOutputStream != null) {
            try {
                // Salva tamanho + dados
                videoOutputStream.write((jpegData.length >> 24) & 0xFF);
                videoOutputStream.write((jpegData.length >> 16) & 0xFF);
                videoOutputStream.write((jpegData.length >> 8) & 0xFF);
                videoOutputStream.write(jpegData.length & 0xFF);
                videoOutputStream.write(jpegData);
                framesRecorded++;
            } catch (Exception e) {
                Log.e(TAG, "Erro salvando frame", e);
            }
        }
    }

    private void showFlashEffect() {
        runOnUiThread(() -> {
            flashImageView.setVisibility(View.VISIBLE);
            flashImageView.setAlpha(0.8f);
            flashImageView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> flashImageView.setVisibility(View.GONE));
        });
    }

    private void showShareOption() {
        runOnUiThread(() -> {
            shareLayout.setVisibility(View.VISIBLE);
            shareLayout.animate().alpha(1f).setDuration(500);
        });
    }

    private void shareLastMedia() {
        if (lastMediaPath == null) {
            Toast.makeText(this, "Nada para compartilhar", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(lastMediaPath);
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.net.Uri uri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType(file.getName().endsWith(".jpg") ? "image/jpeg" : "video/*");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, "Compartilhar"));
        } catch (Exception e) {
            Log.e(TAG, "Erro compartilhando", e);
        }
    }

    private void updateStatusText(String text) {
        runOnUiThread(() -> statusTextView.setText(text));
    }

    private void updateButtonStates() {
        runOnUiThread(() -> {
            buttonRecord.setEnabled(isConnected);
            buttonPhoto.setEnabled(isConnected && !isRecording);
        });
    }
}