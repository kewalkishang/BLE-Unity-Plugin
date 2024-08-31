package com.onlykk.bleunityplugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BluetoothServer {

    private static final String TAG = "ServerManager";

    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattServer bluetoothGattServer;

    private Context context;

    private List<BluetoothDevice> connectedDevices = new ArrayList<>();


    BluetoothServer(BluetoothAdapter bluetoothAdapter, Context context){
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        this.context = context;
    }

    /**
     * Starts the GATT server and begins advertising.
     */
    public void startServer(){
        startAdvertising();
        setupGattServer();
    }


    /**
     * Stops the GATT server and advertising.
     */
    @SuppressLint("MissingPermission")
    public void stopServer() {
        // Disconnect connected clients
        stopGattServer();
        stopAdvertising();

        // Clear the server data
        resetServer();
    }



    /**
     * Starts BLE advertising.
     */
    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BLEPluginManager.SERVICE_UUID))
                .build();

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    /**
     * Callback for advertising events.
     */
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnAdvertisingStartSuccess", "");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnAdvertisingStartFailure", String.valueOf(errorCode));
        }
    };

    /**
     * Sets up the GATT server with services and characteristics.
     */
    @SuppressLint("MissingPermission")
    void setupGattServer() {
        BluetoothGattService service = new BluetoothGattService(BLEPluginManager.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                BLEPluginManager.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        service.addCharacteristic(characteristic);
        bluetoothGattServer = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).openGattServer(context, gattServerCallback);
        bluetoothGattServer.addService(service);
    }

    /**
     * GATT server callback for handling events.
     */
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device);
                String deviceItem = Utils.getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", deviceItem);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device);
                String deviceItem = Utils.getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem);
            }
        }


        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                characteristic.setValue(value);
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
                // msgFromClient += new String(value, StandardCharsets.UTF_8);
                String message = new String(value, StandardCharsets.UTF_8);

                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromClient", message);

            }
        }
    };


    /**
     * Sends data to all connected clients.
     * @param data The data to send
     */
    @SuppressLint("MissingPermission")
    public void sendDataToClient(String data) {
        if (bluetoothGattServer == null || data.isEmpty()) {
            //Toast.makeText(context, "Server not started", Toast.LENGTH_SHORT).show();
            return;
        }
        // Get the characteristic and set the value
        BluetoothGattCharacteristic characteristic = bluetoothGattServer
                .getService(BLEPluginManager.SERVICE_UUID)
                .getCharacteristic(BLEPluginManager.CHARACTERISTIC_UUID);

        characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));

        // Notify all connected devices of the characteristic change
        for (BluetoothDevice device : connectedDevices) {
            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }



    /**
     * Stops the GATT server.
     */
    @SuppressLint("MissingPermission")
    public void stopGattServer() {
        if (bluetoothGattServer == null) {
            Log.d(TAG, "No GATT server to stop");
            return;
        }

        // Forcefully disconnect all clients - Didn't hard disconnect.
        //disconnectAllClients();

        bluetoothGattServer.close();
        bluetoothGattServer = null;


        Log.d(TAG, "GATT server stopped");
    }


    /**
     * Stops BLE advertising.
     */
    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
        }
    }

    /**
     * Disconnects all connected clients from the GATT server.
     */
    @SuppressLint("MissingPermission")
    private void disconnectAllClients() {
        if (bluetoothGattServer == null) {
            Log.d(TAG, "No active GATT server");
            return;
        }

        for (BluetoothDevice device : connectedDevices) {
            bluetoothGattServer.cancelConnection(device);
            Log.d(TAG, "Forcefully disconnecting device: " + device.getAddress());
        }

        // Wait a moment for disconnections to process
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Clear the server data
        resetServer();
    }

    public void resetServer(){
        //deviceMap.clear();
        connectedDevices.clear();
    }
}
