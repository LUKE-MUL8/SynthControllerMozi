package com.example.synthcontroller;

import com.rejowan.rotaryknob.RotaryKnob;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothSocket btSocket;
    private OutputStream btOutput;
    private final String DEVICE_NAME = "HC-02"; // Bluetooth module name
    private final UUID UUID_SERIAL_PORT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private RotaryKnob rotaryKnob;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//
//        // Initialize RotaryKnob
//        rotaryKnob = findViewById(R.id.rotaryKnob);
//
//        // Set initial knob values (optional)
//        rotaryKnob.setMin(0);
//        rotaryKnob.setMax(255);
//        rotaryKnob.setCurrentProgress(0);
//
//        // Set listener for knob progress changes
//        rotaryKnob.setProgressChangeListener(new RotaryKnob.OnProgressChangeListener() {
//            @Override
//            public void onProgressChanged(int progress) {
//                // Send knob progress to Arduino
//                sendKnobValue(progress);
//            }
//        });
//
//        // Bluetooth connection button
//        Button connectButton = findViewById(R.id.connect_button);
//        connectButton.setOnClickListener(v -> connectToBluetooth());

        // Find the button by its ID
        Button navigateButton = findViewById(R.id.navigate_button);

        // Check if the button was found
        if (navigateButton == null) {
            throw new RuntimeException("navigate_button not found in activity_main.xml");
        }

        // Set OnClickListener
        navigateButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PerformActivity.class);
            startActivity(intent);
        });

    }




    private void sendKnobValue(int progress) {
        if (btOutput != null) {
            try {
                btOutput.write(progress);  // Send the progress value (0-255) over Bluetooth
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Error sending knob value");
            }
        }
    }

    private void connectToBluetooth() {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            showToast("Bluetooth not supported on this device");
            return;
        }

        // Check if Bluetooth is enabled
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
            showToast("Enabling Bluetooth...");
        }

        // Get paired devices and try to connect
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            showToast("No paired devices found. Pair with HC-02 first.");
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

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }
}
