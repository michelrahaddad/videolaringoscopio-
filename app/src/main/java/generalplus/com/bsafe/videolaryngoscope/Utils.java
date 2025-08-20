package com.bsafe.videolaryngoscope;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import generalplus.com.GPCamLib.CamWrapper;

public class Utils {
    private static final String TAG = "CameraUtils";
    private CamWrapper mCamWrapper;
    private Context mContext;
    private Handler mActivityHandler;
    private String lastSetDownloadPath;

    public static final int MSG_FILE_DOWNLOAD_SUCCESS = 1003;
    public static final int MSG_FILE_DOWNLOAD_ERROR = 1004;
    public static final int MSG_NO_FILES_FOUND = 1005;

    public Utils(Context context, CamWrapper camWrapper, Handler activityHandler) {
        this.mContext = context;
        this.mCamWrapper = camWrapper;
        this.mActivityHandler = activityHandler;
    }

    public String getLastSetDownloadPath() {
        return lastSetDownloadPath;
    }

    public void retrieveLatestMediaFromDevice() {
        Log.d(TAG, "Tentando recuperar a lista de mídias solicitando a lista completa de arquivos...");
        if (mCamWrapper != null) {
            mCamWrapper.GPCamSendGetFullFileList();
        } else {
            Log.e(TAG, "mCamWrapper é nulo, não é possível enviar GPCamSendGetFullFileList.");
            if (mActivityHandler != null) {
                Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, "CamWrapper não inicializado");
                mActivityHandler.sendMessage(msg);
            }
        }
    }

    public void processCameraFilesAndDownloadLatest(int fileCount) {
        if (mCamWrapper == null) {
            Log.e(TAG, "mCamWrapper é nulo em processCameraFilesAndDownloadLatest.");
            if (mActivityHandler != null) {
                Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, "CamWrapper nulo ao processar arquivos");
                mActivityHandler.sendMessage(msg);
            }
            return;
        }

        if (fileCount == 0) {
            Log.w(TAG, "Nenhum arquivo encontrado no dispositivo da câmera (fileCount é 0).");
            if (mActivityHandler != null) {
                mActivityHandler.sendMessage(Message.obtain(mActivityHandler, MSG_NO_FILES_FOUND));
            }
            return;
        }

        List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            String fileName = mCamWrapper.GPCamGetFileName(i);
            if (fileName != null && !fileName.isEmpty()) {
                fileNames.add(fileName);
                Log.d(TAG, "Arquivo na câmera: " + fileName + " (Tamanho: " + mCamWrapper.GPCamGetFileSize(i) + ")");
            }
        }

        if (fileNames.isEmpty()) {
            Log.w(TAG, "A contagem de arquivos era " + fileCount + " mas nenhum nome de arquivo válido foi recuperado.");
            if (mActivityHandler != null) {
                mActivityHandler.sendMessage(Message.obtain(mActivityHandler, MSG_NO_FILES_FOUND));
            }
            return;
        }

        Collections.sort(fileNames, (s1, s2) -> s2.compareTo(s1)); // Descendente
        String latestFile = fileNames.get(0);
        Log.i(TAG, "Último arquivo identificado na câmera (ordenação heurística): " + latestFile);

        int originalIndexOfLatest = -1;
        for (int i = 0; i < fileCount; i++) {
            if (latestFile.equals(mCamWrapper.GPCamGetFileName(i))) {
                originalIndexOfLatest = i;
                break;
            }
        }

        if (originalIndexOfLatest != -1) {
            downloadFileFromCameraDevice(latestFile, originalIndexOfLatest);
        } else {
            Log.e(TAG, "Não foi possível encontrar o índice original para o último arquivo determinado heuristicamente: " + latestFile);
            if (mActivityHandler != null) {
                Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, "Não foi possível encontrar o índice do arquivo mais recente");
                mActivityHandler.sendMessage(msg);
            }
        }
    }

    private void downloadFileFromCameraDevice(String cameraFileName, int fileIndex) {
        if (mCamWrapper == null) {
            Log.e(TAG, "mCamWrapper é nulo em downloadFileFromCameraDevice.");
            return;
        }

        File storageDir = getOutputDirectory();
        if (storageDir == null) {
            Log.e(TAG, "Falha ao obter o diretório de saída local para download.");
            if (mActivityHandler != null) {
                Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, "Falha ao obter diretório de saída");
                mActivityHandler.sendMessage(msg);
            }
            return;
        }

        this.lastSetDownloadPath = new File(storageDir, cameraFileName).getAbsolutePath();
        Log.i(TAG, "Preparando para baixar o arquivo: " + cameraFileName + " (índice: " + fileIndex + ") para o caminho local: " + lastSetDownloadPath);

        mCamWrapper.SetGPCamSetDownloadPath(lastSetDownloadPath);
        Log.d(TAG, "Diretório de download definido no CamWrapper para: " + lastSetDownloadPath);

        Log.d(TAG, "Solicitando download para o arquivo: " + cameraFileName + " no índice: " + fileIndex);
        int result = mCamWrapper.GPCamSendGetFileRawdata(fileIndex);
        Log.d(TAG, "GPCamSendGetFileRawdata(índice: " + fileIndex + ") retornou: " + result);

        if (result == 0) {
            Log.i(TAG, "Solicitação de download enviada com sucesso para " + cameraFileName);
            Toast.makeText(mContext, "Solicitação de download enviada para: " + cameraFileName, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Falha ao enviar solicitação de download. Resultado: " + result);
            if (mActivityHandler != null) {
                Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, "Falha ao enviar comando de download para " + cameraFileName);
                mActivityHandler.sendMessage(msg);
            }
        }
    }

    public void handleDownloadSuccess(String downloadedFilePath) {
        Log.i(TAG, "Utils: Download do arquivo reportado como bem-sucedido. Caminho: " + downloadedFilePath);
        if (mActivityHandler != null && downloadedFilePath != null && !downloadedFilePath.isEmpty()) {
            Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_SUCCESS, downloadedFilePath);
            mActivityHandler.sendMessage(msg);
        } else {
            Log.w(TAG, "Utils: handleDownloadSuccess chamado com caminho nulo ou Handler nulo.");
        }
    }

    public void handleDownloadError(String reason) {
        Log.e(TAG, "Utils: Download do arquivo reportado como erro. Motivo: " + reason);
        if (mActivityHandler != null) {
            Message msg = Message.obtain(mActivityHandler, MSG_FILE_DOWNLOAD_ERROR, reason);
            mActivityHandler.sendMessage(msg);
        }
    }

    private File getOutputDirectory() {
        File mediaDir = new File(mContext.getExternalFilesDir(null), "BsafeAppMedia");
        if (!mediaDir.exists()) {
            if (!mediaDir.mkdirs()) {
                Log.e(TAG, "Falha ao criar o diretório de mídia: " + mediaDir.getAbsolutePath());
                return null;
            }
        }
        return mediaDir;
    }
}