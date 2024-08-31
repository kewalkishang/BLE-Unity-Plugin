package com.onlykk.bleunityplugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLEScanner {

    private static final String TAG = "BLEScanner";
    private BluetoothLeScanner bluetoothLeScanner;

    private List<String[]> discoveredDevices = new ArrayList<>();

    private boolean scanning = false;

    private Map<String, BluetoothDevice> deviceMap = new HashMap<>();

    BLEScanner(BluetoothAdapter bluetoothAdapter){
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    /**
     * Starts scanning for BLE devices.
     */
    public void startScan(){
        if(bluetoothLeScanner != null) {
            discoveredDevices.clear();
            scanLeDevice();
        }
    }

    /**
     * Scans for BLE devices.
     */
    @SuppressLint("MissingPermission")
    private void scanLeDevice() {

        scanning = true;
        // Set up scan filters
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BLEPluginManager.SERVICE_UUID)).build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bluetoothLeScanner.startScan(filters, settings, leScanCallback);
        UnityPlayer.UnitySendMessage("BLEPlugin", "OnScanStarted", "" );
    }

    /**
     * Stops the BLE device scan.
     */
    @SuppressLint("MissingPermission")
    public void stopScan(){
        if (scanning && bluetoothLeScanner != null) {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
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
            String deviceItem = Utils.getDeviceJson(device);
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
}
