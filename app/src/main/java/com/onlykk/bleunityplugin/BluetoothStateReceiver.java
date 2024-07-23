package com.onlykk.bleunityplugin;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.unity3d.player.UnityPlayer;

public class BluetoothStateReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Log.d(TAG, "Bluetooth is off");
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateChange", "off");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Log.d(TAG, "Bluetooth is turning off");
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateChange", "turning_off");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Log.d(TAG, "Bluetooth is on");
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateChange", "on");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Log.d(TAG, "Bluetooth is turning on");
                    UnityPlayer.UnitySendMessage("BLEPlugin", "OnBluetoothStateChange", "turning_on");
                    break;
            }
        }
    }
}