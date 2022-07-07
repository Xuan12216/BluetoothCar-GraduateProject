package com.example.bluetoothcarver1;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;

public class OpenCvControl extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2
{
    private static final String     TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_cv_control);
    }

    @Override
    public void onCameraViewStarted(int width, int height)
    {

    }

    @Override
    public void onCameraViewStopped()
    {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
    {
        return null;
    }
}