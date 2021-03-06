package com.example.bluetoothcarver1;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import com.example.bluetoothcarver1.Module.Enitiy.ScannedData;

public class DetectNControlActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private ScannedData selectedDevice;
    Button btn_selfControl,btn_opencvControl,btn_disconnect;

    public DetectNControlActivity() { Log.i(TAG, "Instantiated new " + this.getClass()); }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_detect_ncontrol);
        btn_selfControl = (Button) findViewById(R.id.btn_selfControl);
        btn_selfControl.setOnClickListener(onClickListener);
        btn_opencvControl = (Button) findViewById(R.id.btn_opencvControl);
        btn_opencvControl.setOnClickListener(onClickListener);
        btn_disconnect = (Button) findViewById(R.id.btn_disconnect);
        btn_disconnect.setOnClickListener(onClickListener);

        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
    }

    public View.OnClickListener onClickListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View view)
        {
            switch (view.getId())
            {
                case R.id.btn_selfControl:
                    Intent intent = new Intent(DetectNControlActivity.this, SelfControl.class);
                    intent.putExtra(SelfControl.INTENT_KEY,selectedDevice);
                    startActivity(intent);
                    break;
                case R.id.btn_opencvControl:
                    Intent intent1 = new Intent(DetectNControlActivity.this, OpenCvControl.class);
                    intent1.putExtra(OpenCvControl.INTENT_KEY,selectedDevice);
                    startActivity(intent1);
                    break;
                case R.id.btn_disconnect:
                    finish();
                    break;
            }
        }
    };
}