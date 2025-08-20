package com.bsafe.videolaryngoscope;

import android.app.Application;
import android.util.Log;

public class BsafeApplication extends Application {

    private static final String TAG = "BsafeApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Aplicação iniciada");

        // Carrega as bibliotecas nativas necessárias
        loadNativeLibraries();
    }

    private void loadNativeLibraries() {
        // Carrega a biblioteca GPCam (não está sendo usada diretamente, mas pode ser necessária)
        try {
            System.loadLibrary("GPCam");
            Log.i(TAG, "Biblioteca nativa 'libGPCam.so' carregada com sucesso.");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Biblioteca 'libGPCam.so' não encontrada ou não necessária", e);
        }

        // Carrega a biblioteca ffmpeg (essencial para o streaming)
        try {
            System.loadLibrary("ffmpeg");
            Log.i(TAG, "Biblioteca nativa 'libffmpeg.so' carregada com sucesso.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FALHA CRÍTICA ao carregar 'libffmpeg.so'. O streaming não funcionará!", e);
        }
    }
}