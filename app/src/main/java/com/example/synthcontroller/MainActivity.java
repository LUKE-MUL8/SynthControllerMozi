package com.example.synthcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothSocket btSocket;
    private OutputStream btOutput;
    private final String DEVICE_NAME = "HC-02";// Bluetooth module name
    private final UUID UUID_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView frequencyLabel = findViewById(R.id.frequency_label);
        SeekBar frequencySlider = findViewById(R.id.frequency_slider);
        Button connectButton = findViewById(R.id.connect_button);

        connectButton.setOnClickListener(v -> connectToBluetooth());

        frequencySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float frequency = 20.0f + progress; // Map slider (0-1980) to frequency (20-2000 Hz)
                frequencyLabel.setText(String.format("Frequency: %.1f Hz", frequency));
                sendFrequency(frequency);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void requestBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Check if the permission is already granted
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                showToast("Bluetooth permission already granted.");
                return;
            }

            // Request the BLUETOOTH_CONNECT permission
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                    1
            );
        } else {
            showToast("Bluetooth permission not required on this Android version.");
        }
    }



    private void connectToBluetooth() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            showToast("Bluetooth not supported on this device");
            return;
        }

        // Check for Bluetooth permission (for Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission();
            return;
        }

        // Enable Bluetooth if disabled
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
            showToast("Enabling Bluetooth...");
        }

        // Connect to the device
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            showToast("No paired devices found. Pair with HC-05 first.");
            return;
        }

        for (BluetoothDevice device : pairedDevices) {
            if (DEVICE_NAME.equals(device.getName())) {
                try {
                    btSocket = device.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT);
                    btSocket.connect();
                    btOutput = btSocket.getOutputStream();
                    showToast("Connected to " + DEVICE_NAME);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    showToast("Connection failed: " + e.getMessage());
                }
            }
        }

        showToast(DEVICE_NAME + " not found among paired devices.");
    }






    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) { // Bluetooth permission request code
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Bluetooth permission granted. Please try connecting again.");
                connectToBluetooth(); // Retry connection
            } else {
                showToast("Bluetooth permission denied. Cannot connect.");
            }
        }
    }



    private void sendFrequency(float frequency) {
        if (btOutput == null) {
            // Show a message to the user that Bluetooth is not connected
            return;
        }

        try {
            btOutput.write(String.format("%.1f\n", frequency).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            // Show a message to the user about the failure
        }
    }

}
