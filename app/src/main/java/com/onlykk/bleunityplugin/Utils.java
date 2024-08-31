package com.onlykk.bleunityplugin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import org.json.JSONObject;

public class Utils {

    /**
     * Converts a Bluetooth device object to a JSON string.
     * @param device The Bluetooth device
     * @return JSON string representing the device
     */
    @SuppressLint("MissingPermission")
    public static String getDeviceJson(BluetoothDevice device)
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


}
