package com.onlykk.bleunityplugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import com.unity3d.player.UnityPlayer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BluetoothClient {


    private static final String TAG = "ClientManager";

    private Set<String> uniqueDeviceAddresses = new HashSet<>();

    private BluetoothGatt bluetoothGatt;

    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();
    private BluetoothDevice deviceConnected;

    private Context context;

    BluetoothClient(Context context){
        this.context = context;
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
        //connectedDevices.clear();
        deviceConnected = null;
    }



    /**
     * Sends data to the GATT server.
     * @param data The data to send
     */
    @SuppressLint("MissingPermission")
    public void sendDataToServer(String data) {
        if (bluetoothGatt == null || data.isEmpty()) {
            // Toast.makeText(context, "No device connected or data empty", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(BLEPluginManager.SERVICE_UUID).getCharacteristic(BLEPluginManager.CHARACTERISTIC_UUID);
        characteristic.setValue(data.getBytes(StandardCharsets.UTF_8));
        bluetoothGatt.writeCharacteristic(characteristic);
    }



    private boolean hasRequiredServiceAndCharacteristic(ScanResult result) {
        ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord != null) {
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if (serviceUuids != null && serviceUuids.contains(new ParcelUuid(BLEPluginManager.SERVICE_UUID))) {
                byte[] serviceData = scanRecord.getServiceData(new ParcelUuid(BLEPluginManager.SERVICE_UUID));
                if (serviceData != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Connects to a specified BLE device.
     * @param deviceAddress The address of the device to connect to
     */
    @SuppressLint("MissingPermission")
    public void connectToDevice(String deviceAddress) {
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

                String deviceItem = Utils.getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceConnected", deviceItem);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from the GATT server
                Log.d(TAG, "onConnectionStateChange Disconnected");
                BluetoothDevice device = gatt.getDevice();
                String deviceItem = Utils.getDeviceJson(device);
                UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem);

            }else {
                Log.d(TAG, "onConnectionStateChange Random State");
                BluetoothDevice device = gatt.getDevice();
                String deviceItem = Utils.getDeviceJson(device);
                String msg = newState + " : " + status + " : " + newState;
                UnityPlayer.UnitySendMessage("BLEPlugin", "onConnectionStateChange", msg);

            }

        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(BLEPluginManager.SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(BLEPluginManager.CHARACTERISTIC_UUID);
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
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String receivedData = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                if(receivedData != null && receivedData.equals("DisconnectClient"))
                {
                    BluetoothDevice device = gatt.getDevice();
                    String deviceItem = Utils.getDeviceJson(device);
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
