package com.bsafe.videolaryngoscope;

import android.app.Application;
import android.util.Log;

public class BsafeApplication extends Application {

    private static final String TAG = "BsafeApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "=====================================");
        Log.d(TAG, "    B-SAFE VIDEOLARYNGOSCOPE");
        Log.d(TAG, "=====================================");
        Log.d(TAG, "Protocolo: JHCMD sobre UDP");
        Log.d(TAG, "IP do dispositivo: 192.168.100.1");
        Log.d(TAG, "Porta de controle: 20000");
        Log.d(TAG, "Porta de dados: 10900");
        Log.d(TAG, "=====================================");

        // NÃO carregamos bibliotecas nativas pois:
        // 1. libffmpeg.so foi feita para RTSP, não JHCMD
        // 2. libGPCam.so usa protocolo diferente
        // 3. Vamos usar Java puro para JHCMD/UDP
    }
}