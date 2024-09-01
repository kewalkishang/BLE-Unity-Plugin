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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothServer {

    private static final String TAG = "ServerManager";

    private static final String END_MARKER = "END_OF_MSG";
    private static final int CHUNK_SIZE = 20;

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
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
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
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnAdvertisingStartSuccess", BLEPluginManager.SERVICE_UUID.toString());
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

    private StringBuilder receivedData = new StringBuilder();

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

        private int mtu = 20;
        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "Server ReadChanged  : "+  offset);
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
        }

        private StringBuilder receivedData = new StringBuilder();
        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {

                String chunk = new String(value, StandardCharsets.UTF_8);
                receivedData.append(chunk);
                Log.d(TAG, "Server message received: " + chunk);


                // Check if the chunk contains the "END_OF_MSG" marker
                if (receivedData.toString().contains(END_MARKER)) {
                    // Extract the complete message by removing the "END_OF_MSG" marker
                    String completeMessage = receivedData.toString().replace(END_MARKER, "");
                    Log.d(TAG, "Server Complete message received: " + completeMessage);

                    // Clear the buffer for the next message
                    receivedData.setLength(0);

                    // Process the complete message as needed
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromClient", completeMessage);
                }

                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
                Log.d(TAG, "MTU size changed to: " + mtu + " : " + device.getAddress().toString());
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
        Log.d(TAG,  "sendDataToClient : " +data);


        // Append the "END_OF_MSG" marker to indicate the end of the message
        String fullMessage = data + END_MARKER;

        BluetoothGattCharacteristic characteristic = bluetoothGattServer
                .getService(BLEPluginManager.SERVICE_UUID)
                .getCharacteristic(BLEPluginManager.CHARACTERISTIC_UUID);

        int mtu = 20; // Subtract the 3 bytes used by the ATT header
        int dataLength = fullMessage.length();
        int offset = 0;

        while (offset < dataLength) {
            int end = Math.min(offset + mtu, dataLength);
            String packet = fullMessage.substring(offset, end);
            Log.d(TAG, "sendDataToClient Chunk : "+ packet );
            characteristic.setValue(packet.getBytes(StandardCharsets.UTF_8));

            for (BluetoothDevice device : connectedDevices) {
                bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
            }

            offset = end;
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
