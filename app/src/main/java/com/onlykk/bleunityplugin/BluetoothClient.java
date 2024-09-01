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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class BluetoothClient {


    private static final String TAG = "ClientManager";

    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice deviceConnected;

    private Context context;

    private static final String END_MARKER = "END_OF_MSG";
    private static final int CHUNK_SIZE = 20;

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

        // Clear the list of connected devices
        //connectedDevices.clear();
        deviceConnected = null;
    }

    private Queue<String> writeQueue = new LinkedList<>();
    private boolean isWriting = false;

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
        Log.d(TAG,  "Client sendDataToServer : " +data);
        data += END_MARKER;

        // Start the write process if not already writing
        enqueueData(data);

        // Start the write process if not already writing
        if (!isWriting) {
            processNextWrite();
        }
    }

    private void enqueueData(String data) {
        int mtu = 20;
        int offset = 0;
        while (offset < data.length()) {
            int end = Math.min(offset + mtu, data.length());
            writeQueue.add(data.substring(offset, end));
            offset = end;
        }
    }

    @SuppressLint("MissingPermission")
    private void processNextWrite() {
        if (writeQueue.isEmpty()) {
            isWriting = false;
            return;
        }

        isWriting = true;
        String packet = writeQueue.poll();

        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(BLEPluginManager.SERVICE_UUID).getCharacteristic(BLEPluginManager.CHARACTERISTIC_UUID);
        characteristic.setValue(packet.getBytes(StandardCharsets.UTF_8));

        boolean writeSuccess = bluetoothGatt.writeCharacteristic(characteristic);
        if (!writeSuccess) {
            Log.e(TAG, "Failed to write characteristic");
            processNextWrite(); // Try the next packet
        } else {
            Log.d(TAG, "Write characteristic initiated: " + packet);
        }
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
     * @param device The BluetoothDevice to connect to
     */
    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        if(device != null) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
           // bluetoothGatt.requestMtu(100);
            deviceConnected = device;
        }
        else{
            Log.d(TAG, "Device doesn't exist!");
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
                gatt.discoverServices();
                //gatt.requestMtu(100);
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
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BLEPluginManager.CHARACTERISTIC_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                            //maintainConnection(gatt);
                        } else {
                            // If the characteristic is not found, we can disconnect
                            Log.d(TAG, "Descriptor Discovered is null!");
                         //   gatt.disconnect();
                        }
                    } else {
                        // If the service is not found, we can disconnect
                        Log.d(TAG, "characteristic Discovered is null!");
                     //   gatt.disconnect();
                    }
                } else {
                    // If service discovery failed, we should disconnect
                    Log.d(TAG, "Service Discovered is null!");
                    //gatt.disconnect();
                }
            }
        }

        private StringBuilder receivedData = new StringBuilder();
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            if (BLEPluginManager.CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                String chunk = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                receivedData.append(chunk);
                Log.d(TAG, "Client CharChanged chunk : "+  chunk);
                Log.d(TAG, "Client CharChanged  receivedData: "+  receivedData.toString());
                // Check if the chunk contains the "END_OF_MSG" marker
                if (receivedData.toString().contains(END_MARKER)) {
                    // Extract the complete message by removing the "END_OF_MSG" marker
                    String completeMessage = receivedData.toString().replace(END_MARKER, "");
                    Log.d(TAG, "Complete message received: " + completeMessage);

                    // Clear the buffer for the next message
                    receivedData.setLength(0);
                    if(completeMessage != null && completeMessage.equals("DisconnectClient"))
                {
                    BluetoothDevice device = gatt.getDevice();
                    String deviceItem = Utils.getDeviceJson(device);
                    stopClient();
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnDeviceDisconnected", deviceItem );
                }
                    else {
                        // Process the complete message as needed
                        UnityPlayer.UnitySendMessage("BLEPlugin", "OnDataReceivedFromServer", completeMessage);
                    }
                }

            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
            } else {
                Log.e(TAG, "Characteristic write failed with status: " + status);
            }

            // Process the next write in the queue
            processNextWrite();
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU size changed successfully to: " + mtu);
            } else {
                Log.d(TAG, "Failed to change MTU size, status: " + status);
            }
        }
    };






}
