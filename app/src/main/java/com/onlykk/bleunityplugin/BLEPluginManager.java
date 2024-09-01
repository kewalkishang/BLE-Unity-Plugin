package com.onlykk.bleunityplugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import java.util.Map;
import java.util.UUID;
import com.unity3d.player.UnityPlayer;

public class BLEPluginManager {


    private static final String TAG = "BLEPluginManager";
    private static BLEPluginManager mInstance = null;


    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private Context context;

    private BluetoothServer server;
    private BLEScanner scanner;
    private BluetoothClient client;

    private BluetoothStateReceiver bluetoothStateReceiver;


    public static UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"); // Default UUID
    public static UUID CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"); // Default UUID



    // Method to check if the plugin is loaded successfully
    public static String checkPluginLoad() {
        return "Plugin : " + TAG;
    }


    // Singleton instance of the BLEPluginManager
    public static BLEPluginManager getInstance() {
        if (mInstance == null)
            mInstance = new BLEPluginManager();
        return mInstance;
    }


    /**
     * Initializes the BLE plugin.
     * @param context Application context
     * @param SERVICE_UUID_HOST String UUID for the service
     * @param CHARACTERISTIC_UUID_HOST String UUID for the characteristic
     */
    public void initBLEPlugin(Context context, String SERVICE_UUID_HOST, String CHARACTERISTIC_UUID_HOST) {

        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            UnityPlayer.UnitySendMessage("BLEPlugin", "OnBLEPluginInitialized", "BluetoothAdapter is Null!");
            return;
        }

        CHARACTERISTIC_UUID = UUID.fromString(CHARACTERISTIC_UUID_HOST);
        SERVICE_UUID = UUID.fromString(SERVICE_UUID_HOST);
        bluetoothStateReceiver = new BluetoothStateReceiver();
        server = new BluetoothServer(bluetoothAdapter, context);
        scanner = new BLEScanner(bluetoothAdapter);
        client = new BluetoothClient(context);
        UnityPlayer.UnitySendMessage("BLEPlugin", "OnBLEPluginInitialized", "BluetoothAdapter Initialized!");
    }


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
     * Starts the GATT server and begins advertising.
     */
    public void startServer(){
        if(server != null) {
            server.startServer();
        }
    }


    /**
     * Stops the GATT server and advertising.
     */
    @SuppressLint("MissingPermission")
    public void stopServer() {
        if(server != null){
            server.stopServer();
        }
    }


    /**
     * Starts scanning for BLE devices.
     */
    public void startScan(){
        if(scanner != null) {
            scanner.startScan();
        }
    }


    /**
     * Stops the BLE device scan.
     */
    @SuppressLint("MissingPermission")
    public void stopScan(){
        if(scanner != null) {
            scanner.stopScan();
        }
    }


    /**
     * Connects to a specified BLE device.
     * @param deviceAddress The address of the device to connect to
     */
    @SuppressLint("MissingPermission")
    public void connectToDevice(String deviceAddress) {
        if(scanner != null) {
            Map<String, BluetoothDevice> deviceMap = scanner.getDeviceMap();
            if (client != null && deviceMap.containsKey(deviceAddress)) {
                client.connectToDevice(deviceMap.get(deviceAddress));
            }
            else{
                Log.d(TAG, "Client or DeviceMap doesn't exist!");
            }
        }
        else{
            Log.d(TAG, "Scanner doesn't exist!");
        }
    }


    /**
     * Stops the GATT client.
     */
    @SuppressLint("MissingPermission")
    public void stopClient() {
        if(client != null) {
            client.stopClient();
        }
    }


    /**
     * Sends data to the GATT server.
     * @param data The data to send
     */
    @SuppressLint("MissingPermission")
    public void sendDataToServer(String data) {
        if(client != null) {
            client.sendDataToServer(data);
        }
    }


    /**
     * Sends data to all connected clients.
     * @param data The data to send
     */
    @SuppressLint("MissingPermission")
    public void sendDataToClient(String data) {
        if(server != null) {
            server.sendDataToClient(data);
        }
    }

}
