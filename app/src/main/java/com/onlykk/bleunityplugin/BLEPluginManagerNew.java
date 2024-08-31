package com.onlykk.bleunityplugin;

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
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import com.unity3d.player.UnityPlayer;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BLEPluginManagerNew {

    private static final String TAG = "BLEPluginManager";

    private static BLEPluginManagerNew mInstance = null;

    // Singleton instance of the BLEPluginManager
    public static BLEPluginManagerNew getInstance() {
        if (mInstance == null)
            mInstance = new BLEPluginManagerNew();
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

    private BluetoothDevice deviceConnected;

    private boolean scanning;


    private static UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"); // Default UUID
    private static UUID CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"); // Default UUID

    private List<String[]> discoveredDevices = new ArrayList<>();
    private Set<String> uniqueDeviceAddresses = new HashSet<>();

    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();


    // Method to check if the plugin is loaded successfully
    public static String checkPluginLoad() {
        return "Plugin : " + TAG;
    }


    /**
     * Initializes the BLE plugin.
     * @param context Application context
     * @param SERVICE_UUID_HOST String UUID for the service
     * @param CHARACTERISTIC_UUID_HOST String UUID for the characteristic
     */
    public void initBLEPlugin(Context context, String SERVICE_UUID_HOST, String CHARACTERISTIC_UUID_HOST) {
        this.context = context;

        bluetoothStateReceiver = new BluetoothStateReceiver();
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBLEPluginInitialized", "BluetoothAdapter is Null!");
            return;
        }

        CHARACTERISTIC_UUID = UUID.fromString(CHARACTERISTIC_UUID_HOST);
        SERVICE_UUID = UUID.fromString(SERVICE_UUID_HOST);
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        UnityPlayer.UnitySendMessage("BLEPlugin", "OnBLEPluginInitialized", "BluetoothAdapter Initialized!");
    }


   // private static final int REQUEST_ENABLE_BT = 1;

    /**
     * Enables the Bluetooth adapter if it is not already enabled.
     *  This method was deprecated in API level 33.
     * Starting with Build.VERSION_CODES.TIRAMISU, applications are not allowed to enable/disable Bluetooth.
     */
    @SuppressLint("MissingPermission")
    public void enableBluetoothAdapter() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            boolean success = bluetoothAdapter.enable();
            if (success) {
                Log.d(TAG, "Bluetooth enabled programmatically");
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothEnabled", "Enabled programmatically");
            } else {
                Log.e(TAG, "Failed to enable Bluetooth programmatically");
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothError", "Failed to enable");
            }
        } else if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is already enabled");
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothEnabled", "Already enabled");
        } else {
            Log.e(TAG, "Bluetooth adapter is null");
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothError", "Bluetooth not supported");
        }
    }

    /**
     * Registers a BroadcastReceiver to listen for Bluetooth state changes.
     */
    public void enableBluetoothStateReceiver(){
        try {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(bluetoothStateReceiver, filter);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverEnabled", "ENABLED");
        }catch (IllegalArgumentException e) {
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverEnabled", "FAILED");
        }
    }

    /**
     * Unregisters the Bluetooth state BroadcastReceiver.
     */
    public void disableBluetoothStateReceiver(){
        try {
            context.unregisterReceiver(bluetoothStateReceiver);
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverDisabled", "SUCCESS");
        } catch (IllegalArgumentException e) {
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateReceiverDisabled", "FAILED");
        }
    }


    /**
     * Returns the name of the user's Bluetooth device.
     * @return Bluetooth device name
     */
    @SuppressLint("MissingPermission")
    public String getBluetoothDeviceName(){
        if(bluetoothAdapter != null)
        {
            return bluetoothAdapter.getName();
        }
        return "BLE : Device doesn't exist";
    }

    /**
     * Checks if Bluetooth is supported on the device.
     * @return true if Bluetooth is supported, false otherwise
     */
    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    /**
     * Checks if Bluetooth is enabled.
     * @return true if Bluetooth is enabled, false otherwise
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Starts scanning for BLE devices.
     */
    private void startScan(){
        if(bluetoothLeScanner != null) {
            discoveredDevices.clear();
            scanLeDevice(true);
        }
    }

    /**
     * Scans for BLE devices.
     * @param enable true to start scanning, false to stop
     */
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

    /**
     * Stops the BLE device scan.
     */
    @SuppressLint("MissingPermission")
    private void stopScan(){
        scanning = false;
        bluetoothLeScanner.stopScan(leScanCallback);
    }

    /**
     * Callback for BLE scan results.
     */
    @SuppressLint("MissingPermission")
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            // Check if the device has the required characteristic
           // if (hasRequiredServiceAndCharacteristic(result)) {
                Log.d(TAG, "Found Device " + device.getName() + " :" + device.getAddress());
                String address = device.getAddress();
                deviceMap.put(address, device);
                //String deviceItem = device.getName() + " - " + device.getAddress();
                String deviceItem = getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "UpdateDeviceList", deviceItem);
                discoveredDevices.add(new String[]{device.getName(), device.getAddress()});
          //  }
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


    private boolean hasRequiredServiceAndCharacteristic(ScanResult result) {
        ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord != null) {
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if (serviceUuids != null && serviceUuids.contains(new ParcelUuid(SERVICE_UUID))) {
                byte[] serviceData = scanRecord.getServiceData(new ParcelUuid(SERVICE_UUID));
                if (serviceData != null) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Starts the GATT server and begins advertising.
     */
    private void startServer(){
        setupGattServer();
        startAdvertising();
    }

    /**
     * Stops the GATT server and advertising.
     */
    @SuppressLint("MissingPermission")
    public void stopServer() {
        // Disconnect connected clients
        stopAdvertising();
        stopGattServer();

        // Clear the device map
        deviceMap.clear();
        // Clear the list of connected devices
        connectedDevices.clear();
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
     * Stops the GATT client.
     */
    @SuppressLint("MissingPermission")
    public void stopClient() {
        if (bluetoothGatt == null) {
            Log.d(TAG, "No Client to Disconnect!");
            return;
        }

        bluetoothGatt.disconnect();
        bluetoothGatt.close();
        bluetoothGatt = null;

        deviceMap.clear();
        // Clear the list of connected devices
        connectedDevices.clear();
        deviceConnected = null;
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

        // Clear the device map
        deviceMap.clear();
        // Clear the list of connected devices
        connectedDevices.clear();
    }


    /**
     * Stops BLE advertising.
     */
    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
        }
    }


    /**
     * Returns a list of details about discovered devices.
     * @return Array of device details
     */
    public String[] getDiscoveredDeviceDetails() {
        List<String> deviceDetails = new ArrayList<>();
        for (String[] device : discoveredDevices) {
            deviceDetails.add(device[0] + " - " + device[1]);
        }
        return deviceDetails.toArray(new String[0]);
    }


    private List<BluetoothDevice> connectedDevices = new ArrayList<>();


    /**
     * Starts BLE advertising.
     */
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
    public void setupGattServer() {
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

    /**
     * GATT server callback for handling events.
     */
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device);
                String deviceItem =getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", deviceItem);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device);
                String deviceItem =getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem);
            }
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
    private void sendDataToClient(String data) {
        if (bluetoothGattServer == null || data.isEmpty()) {
            //Toast.makeText(context, "Server not started", Toast.LENGTH_SHORT).show();
            return;
        }
        // Get the characteristic and set the value
        BluetoothGattCharacteristic characteristic = bluetoothGattServer
                .getService(SERVICE_UUID)
                .getCharacteristic(CHARACTERISTIC_UUID);

        characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));

        // Notify all connected devices of the characteristic change
        for (BluetoothDevice device : connectedDevices) {
            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }

    /**
     * Sends data to the GATT server.
     * @param data The data to send
     */
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

    /**
     * Converts a Bluetooth device object to a JSON string.
     * @param device The Bluetooth device
     * @return JSON string representing the device
     */
    @SuppressLint("MissingPermission")
    private String getDeviceJson(BluetoothDevice device)
    {
        // Create JSON object  - java.lang.ClassNotFoundException: com.google.gson.JsonObject
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", device.getName());
            jsonObject.put("address", device.getAddress());
            String deviceItemJson = jsonObject.toString();
            return deviceItemJson;
        }
        catch (Exception e)
        {
            return null;
        }
 }

    /**
     * Connects to a specified BLE device.
     * @param deviceAddress The address of the device to connect to
     */
    @SuppressLint("MissingPermission")
    private void connectToDevice(String deviceAddress) {
        if(deviceMap.containsKey(deviceAddress)) {
            BluetoothDevice device = deviceMap.get(deviceAddress);
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            deviceConnected = device;
        }
        else{
            Log.d(TAG, "DeviceAddress doesn't exist!");
        }
    }


    /**
     * Callback for GATT client events.
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Successfully connected to the GATT server
                Log.d(TAG, "onConnectionStateChange Connected");
                bluetoothGatt.discoverServices();
                BluetoothDevice device = gatt.getDevice();

                String deviceItem = getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", deviceItem);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from the GATT server
                Log.d(TAG, "onConnectionStateChange Disconnected");
                BluetoothDevice device = gatt.getDevice();
                String deviceItem = getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem);

            }else {
                Log.d(TAG, "onConnectionStateChange Random State");
                BluetoothDevice device = gatt.getDevice();
                String deviceItem = getDeviceJson(device);
                String msg = newState + " : " + status + " : " + newState;
                UnityPlayer.UnitySendMessage("BLEPlugin", "onConnectionStateChange", msg);

            }

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
                            //maintainConnection(gatt);
                        } else {
                            // If the characteristic is not found, we can disconnect
                            gatt.disconnect();
                        }
                    } else {
                        // If the service is not found, we can disconnect
                        gatt.disconnect();
                    }
                } else {
                    // If service discovery failed, we should disconnect
                    gatt.disconnect();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                if(receivedData != null && receivedData.equals("DisconnectClient"))
                {
                    BluetoothDevice device = gatt.getDevice();
                    String deviceItem = getDeviceJson(device);
                    stopClient();
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem );
                }
                else {
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromServer", receivedData);
                }
            }
        }
    };

}
