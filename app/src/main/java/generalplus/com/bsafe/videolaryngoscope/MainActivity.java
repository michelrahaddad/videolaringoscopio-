package com.bsafe.videolaryngoscope;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Button connectButton;
    private TextView statusText;

    private static final String WIFI_SSID = "Simple-a09f10fdc3fc";
    private static final String DEVICE_STATIC_IP = "192.168.100.1";
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 1;
    private static final int CONNECTION_TIMEOUT_MS = 30000; // Timeout de 30 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_connect);
        initializeViews();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void initializeViews() {
        connectButton = findViewById(R.id.button_connect);
        statusText = findViewById(R.id.status_text);
        TextView wifiInfo = findViewById(R.id.wifi_info);
        TextView appVersion = findViewById(R.id.app_version);

        statusText.setText("Pronto para conectar");
        wifiInfo.setText("Rede: " + WIFI_SSID);

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            appVersion.setText("v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        connectButton.setOnClickListener(v -> checkAndRequestLocationPermission());
    }

    private void checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_LOCATION_PERMISSION);
        } else {
            initiateWifiConnection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initiateWifiConnection();
        } else {
            Toast.makeText(this, "A permissão de localização é necessária para encontrar a rede Wi-Fi.", Toast.LENGTH_LONG).show();
        }
    }

    private void initiateWifiConnection() {
        statusText.setText("Conectando...");
        connectButton.setEnabled(false);
        Toast.makeText(this, "Procurando rede: " + WIFI_SSID, Toast.LENGTH_SHORT).show();

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder().setSsid(WIFI_SSID).build();
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build();

        unregisterNetworkCallback();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.i(TAG, "Rede Wi-Fi do dispositivo conectada.");
                connectivityManager.bindProcessToNetwork(network);
                runOnUiThread(() -> proceedToCameraActivity());
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                Log.w(TAG, "Rede Wi-Fi indisponível. Verifique se o dispositivo está ligado e próximo.");
                runOnUiThread(() -> {
                    statusText.setText("Rede não encontrada");
                    connectButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Falha ao conectar. Verifique se o Wi-Fi '" + WIFI_SSID + "' está visível e tente novamente.", Toast.LENGTH_LONG).show();
                });
            }
        };

        connectivityManager.requestNetwork(networkRequest, networkCallback, CONNECTION_TIMEOUT_MS);
    }

    private void proceedToCameraActivity() {
        Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        intent.putExtra("DEVICE_IP_ADDRESS", DEVICE_STATIC_IP);
        startActivity(intent);
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                // Ignorar erro
            } finally {
                networkCallback = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectButton.setEnabled(true);
        statusText.setText("Pronto para conectar");
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterNetworkCallback();
    }
}