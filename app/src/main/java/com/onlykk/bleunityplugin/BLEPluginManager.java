package com.onlykk.bleunityplugin;


import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.unity3d.player.UnityPlayer;

public class BLEPluginManager {

    private static final String TAG = "BLEPluginManager";

    public static String checkPluginLoad() {
        return "Loaded " + TAG;
    }

    private static BLEPluginManager mInstance = null;
    public static BLEPluginManager getInstance() {
        if (mInstance == null)
            mInstance = new BLEPluginManager();
        return mInstance;
    }
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private Context context;

    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothGatt bluetoothGatt;

    private BluetoothStateReceiver bluetoothStateReceiver;

    private boolean scanning;

    public String msgFromClient = "Client EPT :";
    public String msgFromServer = "Server EPT :";

    private static final UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"); // Example UUID
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"); // Example UUID

    private List<String[]> discoveredDevices = new ArrayList<>();
    private Set<String> uniqueDeviceAddresses = new HashSet<>();

    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();

    public void initBLEPlugin(Context context) {
        this.context = context;

        bluetoothStateReceiver = new BluetoothStateReceiver();
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
           // Toast.makeText(context, "BLE not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
    }

    public void enableBluetoothStateReceiver(){
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(bluetoothStateReceiver, filter);
        UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverEnabled", "ENABLED");
    }

    public void disableBluetoothStateReceiver(){
        try {
            context.unregisterReceiver(bluetoothStateReceiver);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverDisabled", "SUCCESS");
        } catch (IllegalArgumentException e) {
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverDisabled", "FAILED");
        }
    }

    @SuppressLint("MissingPermission")
    public String getBluetoothDeviceName(){
        if(bluetoothAdapter != null)
        {
            return bluetoothAdapter.getName();
        }
        return "BLE : Device doesn't exist";
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    // Method to check if Bluetooth is enabled
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private void startScan(){
        scanLeDevice(true);
    }

    private void startServer(){
        setupGattServer();
        startAdvertising();
    }

    public void stopServer() {
        stopAdvertising();
        stopGattServer();
    }

    @SuppressLint("MissingPermission")
    public void stopGattServer() {
        if (bluetoothGattServer == null) {
            Log.d(TAG, "No GATT server to stop");
            return;
        }

        bluetoothGattServer.close();
        bluetoothGattServer = null;
        Log.d(TAG, "GATT server stopped");
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
        }
    }

    public String[] getDiscoveredDeviceDetails() {
        List<String> deviceDetails = new ArrayList<>();
        for (String[] device : discoveredDevices) {
            deviceDetails.add(device[0] + " - " + device[1]);
        }
        return deviceDetails.toArray(new String[0]);
    }
    private List<BluetoothDevice> connectedDevices = new ArrayList<>();
    @SuppressLint("MissingPermission")
    private void sendDataToClient(String data) {
        if (bluetoothGattServer == null || data.isEmpty()) {
            //Toast.makeText(context, "Server not started", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothGattCharacteristic characteristic = bluetoothGattServer
                .getService(SERVICE_UUID)
                .getCharacteristic(CHARACTERISTIC_UUID);

        characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));
        for (BluetoothDevice device : connectedDevices) {
            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }

    @SuppressLint("MissingPermission")
    private void sendDataToServer(String data) {
        if (bluetoothGatt == null || data.isEmpty()) {
           // Toast.makeText(context, "No device connected or data empty", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(SERVICE_UUID).getCharacteristic(CHARACTERISTIC_UUID);
        characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    @SuppressLint("MissingPermission")
    private void startAdvertising() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnAdvertisingStartSuccess", "");

            //runOnUiThread(() -> Toast.makeText(MainActivity.this, "Advertising started", Toast.LENGTH_SHORT).show());
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnAdvertisingStartFailure", String.valueOf(errorCode));

            // runOnUiThread(() -> Toast.makeText(MainActivity.this, "Advertising failed: " + errorCode, Toast.LENGTH_SHORT).show());
        }
    };

    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        service.addCharacteristic(characteristic);
        bluetoothGattServer = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).openGattServer(context, gattServerCallback);
        bluetoothGattServer.addService(service);
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            // runOnUiThread(() -> {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
               // Toast.makeText(context, "Device connected: " + device.getAddress(), Toast.LENGTH_SHORT).show();
                connectedDevices.add(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", device.getAddress());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
               // Toast.makeText(context, "Device disconnected: " + device.getAddress(), Toast.LENGTH_SHORT).show();
                connectedDevices.remove(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", device.getAddress());

            }
            //  });
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                characteristic.setValue(value);
                if (responseNeeded) {
                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
                msgFromClient += new String(value, StandardCharsets.UTF_8);
                String message = device.getAddress() + "|" + new String(value, StandardCharsets.UTF_8);

                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromClient", message);

            }
        }
    };

    @SuppressLint("MissingPermission")
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            scanning = true;
            // Set up scan filters
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
            filters.add(filter);

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bluetoothLeScanner.startScan(filters, settings, leScanCallback);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnScanStarted", "" );
        } else {
            stopScan();
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan(){
        scanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
    }


    @SuppressLint("MissingPermission")
    // Device scan callback.
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "Found Device " + device.getName() + " :" + device.getAddress());
            String address = device.getAddress();
          //  if (!uniqueDeviceAddresses.contains(address)) {
               // uniqueDeviceAddresses.add(address);
                deviceMap.put(address, device);
                String deviceItem = device.getName() + " - " + device.getAddress();
                UnityPlayer.UnitySendMessage("BLEPlugin", "UpdateDeviceList", deviceItem );
                discoveredDevices.add(new String[]{device.getName(), device.getAddress()});
           // }
            // Add the device to the RecyclerView adapter
            // runOnUiThsread(() -> deviceAdapter.addDevice(device));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            // Handle batch scan results
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            // Handle scan failure
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnScanFailed", String.valueOf(errorCode));

        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(String deviceAddress) {
        if(deviceAddress.contains(deviceAddress)) {
            BluetoothDevice device = deviceMap.get(deviceAddress);
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
        else{
            Log.d(TAG, "DeviceAddress doesn't exist!");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //  runOnUiThread(() -> {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Successfully connected to the GATT server
                Log.d(TAG, "onConnectionStateChange Connected");
             //   Toast.makeText(context, "Connected to device: " + gatt.getDevice().getAddress(), Toast.LENGTH_LONG).show();
                bluetoothGatt.discoverServices();
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", gatt.getDevice().getAddress());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from the GATT server
                Log.d(TAG, "onConnectionStateChange Disconnected");
             //   Toast.makeText(context, "Disconnected from device: " + gatt.getDevice().getAddress(), Toast.LENGTH_LONG).show();
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", gatt.getDevice().getAddress());

            }
            //   });
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        gatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                final String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromServer", receivedData);

                // runOnUiThread(() -> debugText.setText(receivedData));
            }
        }



        //        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                // Characteristic read successfully
//                // Handle the characteristic data here
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                // Characteristic written successfully
//                if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
//                    final String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
//                    runOnUiThread(() -> debugText.setText(receivedData));
//                }
//            }
//        }
    };

    public String getMsgFromClient() {
        return msgFromClient;
    }

    public String getMsgFromServer() {
        return msgFromServer;
    }

    public void testCallback() {
        Log.d(TAG, "Test callback in android");
        callUnityFunction();
    }

    public void callUnityFunction(){
        Log.d(TAG, "caLLING UNITY");
        UnityPlayer.UnitySendMessage("BLEPlugin", "ChangeText", "Unity" );
    }

}
