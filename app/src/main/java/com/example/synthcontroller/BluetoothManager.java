package com.example.synthcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothManager {
    private static final String TAG = "BluetoothManager";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String BT_DEVICE_NAME = "ESP32Synth";

    private static BluetoothManager instance;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private volatile boolean isConnected = false; // Volatile for thread safety

    private BluetoothManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static BluetoothManager getInstance() {
        if (instance == null) {
            synchronized (BluetoothManager.class) {
                if (instance == null) {
                    instance = new BluetoothManager();
                }
            }
        }
        return instance;
    }

    public synchronized boolean connect() {
        if (isConnected && isSocketValid()) {
            Log.d(TAG, "Already connected to " + BT_DEVICE_NAME);
            return true;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or not enabled");
            return false;
        }

        disconnect(); // Clean up any stale connections

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice device = null;
        for (BluetoothDevice pairedDevice : pairedDevices) {
            if (BT_DEVICE_NAME.equals(pairedDevice.getName())) {
                device = pairedDevice;
                break;
            }
        }

        if (device == null) {
            Log.e(TAG, "Device " + BT_DEVICE_NAME + " not found in paired devices");
            return false;
        }

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            isConnected = true;
            Log.d(TAG, "Successfully connected to " + BT_DEVICE_NAME);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect", e);
            disconnect();
            return false;
        }
    }

    public synchronized void disconnect() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing Bluetooth resources", e);
        } finally {
            isConnected = false;
        }
    }

    public synchronized boolean sendCommand(String command) {
        if (!isConnected || !isSocketValid()) {
            Log.w(TAG, "Not connected or socket invalid, attempting to reconnect");
            if (!connect()) {
                Log.e(TAG, "Reconnection failed, cannot send command: " + command);
                return false;
            }
        }
        try {
            outputStream.write((command + "\n").getBytes());
            outputStream.flush();
            Log.d(TAG, "Sent command: " + command);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to send command: " + command, e);
            isConnected = false; // Mark as disconnected on failure
            return false;
        }
    }

    public boolean isConnected() {
        return isConnected && isSocketValid();
    }

    private boolean isSocketValid() {
        return bluetoothSocket != null && bluetoothSocket.isConnected() && outputStream != null;
    }
}