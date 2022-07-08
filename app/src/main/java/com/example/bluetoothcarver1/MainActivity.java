package com.example.bluetoothcarver1;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity
{
    private static final String TAG = "MainActivity";

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button ScanBluetooth = findViewById(R.id.Bluetooth);
        ScanBluetooth.setOnClickListener(onClickListener);

        Button JumpBtn = findViewById(R.id.btn_jump);
        JumpBtn.setOnClickListener(onClickListener);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
    }

    public View.OnClickListener onClickListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            switch (v.getId())
            {
                case R.id.Bluetooth:
                    startActivity(new Intent(MainActivity.this, ScanBluetooth.class));
                    break;
                case R.id.btn_jump:
                    startActivity(new Intent(MainActivity.this,OpenCvControl.class));
                default:
            }
        }
    };
}
