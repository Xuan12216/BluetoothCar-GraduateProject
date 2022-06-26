package com.example.bluetoothcarver1;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.example.bluetoothcarver1.Module.Enitiy.ScannedData;
import com.example.bluetoothcarver1.Module.Service.BluetoothLeService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity
{
    private static final String     TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";

    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onBluetooth(View v)
    {
        startActivity(new Intent(MainActivity.this, ScanBluetooth.class));
    }
}