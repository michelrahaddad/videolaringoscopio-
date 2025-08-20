package com.bsafe.videolaryngoscope;

import android.app.Application;
import android.util.Log;

public class BsafeApplication extends Application {

    private static final String TAG = "BsafeApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // Ponto CRÍTICO para o funcionamento do app.
        // Carrega as bibliotecas nativas de forma centralizada ao iniciar o aplicativo.
        try {
            System.loadLibrary("GPCam");
            Log.i(TAG, "Biblioteca nativa 'libGPCam.so' carregada com sucesso.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FALHA CRÍTICA ao carregar 'libGPCam.so'. Verifique se o arquivo .so está nas pastas jniLibs.", e);
        }

        try {
            System.loadLibrary("ffmpeg");
            Log.i(TAG, "Biblioteca nativa 'libffmpeg.so' carregada com sucesso.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FALHA CRÍTICA ao carregar 'libffmpeg.so'. Verifique se o arquivo .so está nas pastas jniLibs.", e);
        }
    }
}