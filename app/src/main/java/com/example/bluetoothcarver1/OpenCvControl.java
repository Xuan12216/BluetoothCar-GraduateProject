package com.example.bluetoothcarver1;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.bluetoothcarver1.Module.Enitiy.ScannedData;
import com.example.bluetoothcarver1.Module.Service.BluetoothLeService;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.Collections;
import java.util.List;

public class OpenCvControl extends CameraActivity
{
    //Bluetooth
    private static final String TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    private TextView tvAddress,tvStatus,tvRespond;
    private boolean isLedOn = false;

    //OpenCV
    //Application settings
    private static final int SETTINGS = 10;
    //Current screen orientation
    public static int orientation;
    //Height of image frame
    private int height;
    //Width of image frame
    private int width;
    //Working matrices
    private Mat matRgba;
    private Mat matGray;
    private Mat matEdges;
    private Mat lines;
    private Bitmap edgeBitmap;
    //camera bridge
    private CameraBridgeViewBase openCvCameraView;
    //How many votes in hough space should indicate line
    private int lineThreshold;
    //Minimum line segment length
    private int minLineSize;
    //Maximum length of gap between line segments
    private int maxLineGap;
    //-----------------------------------------------

    // 通过OpenCV管理Android服务，异步初始化OpenCV
    private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this)
    {
        @Override
        public void onManagerConnected(int status)
        {
            switch (status)
            {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCvCameraView.enableView();
                }
                break;
                default:
                {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    /**
     * Initialize default parameters
     */
    public OpenCvControl()
    {
        super();
        lineThreshold = 70;
        minLineSize = 100;
        maxLineGap = 100;
        orientation = 1;
        width = 0;
        height = 0;
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_open_cv_control);

        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        //initBLE();
        //initUI();

        // 实现绑定和添加事件监听
        openCvCameraView = findViewById(R.id.hough_activity_surface_view);
        openCvCameraView.setCvCameraViewListener(cvCameraViewListener);

        //Register accelerometer sensor handler for determining current screen orientation
        SensorManager sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(new SensorEventListener()
        {
            int orientation = -1;
            @Override
            public void onSensorChanged(SensorEvent event)
            {
                if (event.values[1] < 6.5 && event.values[1] > -6.5)
                {
                    if (orientation != 1)
                        OpenCvControl.orientation = 1;
                    orientation = 1;
                }
                else
                {
                    if (orientation != 0)
                        OpenCvControl.orientation = 0;
                    orientation = 0;
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        }, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }

    //----以下是 OpenCV 的功能----//
    @Override
    public void onPause()
    {
        super.onPause();
        if (openCvCameraView != null)
            openCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        // 每次当前Activity激活都会调用此方法，所以可以在此处检测OpenCV的库文件是否加载完毕
        super.onResume();
        if (!OpenCVLoader.initDebug())
        {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, loaderCallback);
        }
        else
        {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy()
    {
        super.onDestroy();
        if (openCvCameraView != null)
            openCvCameraView.disableView();
    }

    private CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2()
    {
        // 对象实例化以及基本属性的设置，包括：长度、宽度和图像类型标志
        @Override
        public void onCameraViewStarted(int width_scr, int height_scr)
        {
            width = width_scr;
            height = height_scr;
            matRgba = new Mat(height, width, CvType.CV_8UC4);
            matGray = new Mat(height, width, CvType.CV_8UC1);
            matEdges = new Mat(height, width, CvType.CV_8UC1);
            edgeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        }

        @Override
        public void onCameraViewStopped()
        {
            matRgba.release();
            matGray.release();
            matEdges.release();
            edgeBitmap.recycle();
        }

        /**
         * Process image frames from camera in real-time and draw them back
         *
         * @param inputFrame
         */
        @Override
        public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
        {
            matRgba = inputFrame.rgba();
            matGray = inputFrame.gray();

            //Bottom half of landscape image
            matGray.submat(height / 2 -100, height, 250, width-250).copyTo(matEdges.submat(height / 2 -100, height, 250, width-250));
            // Adaptive threshold
            Imgproc.adaptiveThreshold(matEdges, matEdges, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 3, -1.5);
            //Delete noise (little white points)
            Imgproc.erode(matEdges, matEdges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2)));

            openCVLineSegments();

            return matRgba;
        }
    };

    /**
     * Lower threshold for portrait orientation and for detecting road lanes with horizon
     *
     * @return lineThreshold
     */
    private int getLineThreshold()
    {
        int actualThresh =  lineThreshold;

        return actualThresh;
    }

    /**
     * Draw detected lines to output image from temporary matrix
     *
     * @param tmp
     */
    private void drawTmpToMRgba(Mat tmp)
    {
        //draw line
        Imgproc.line(matRgba, new Point(250, height / 2 - 101), new Point(width-250, height / 2 - 101), new Scalar(0, 255, 0), 2);
        Imgproc.line(matRgba, new Point(250, height / 2 - 101), new Point(250, height), new Scalar(0, 255, 0), 2);
        Imgproc.line(matRgba, new Point(width-250, height / 2 - 101), new Point(width-250, height), new Scalar(0, 255, 0), 2);

        if (tmp != null)
            tmp.submat(height / 2, height, 0, width).copyTo(matRgba.submat(height / 2, height, 0, width));
    }

    private void openCVLineSegments()
    {
        //Matrix of detected line segments
        lines = new Mat();
        //Line segments detection
        Imgproc.HoughLinesP(matEdges, lines, 1, Math.PI / 180, getLineThreshold(), minLineSize, maxLineGap);
        //Draw line segments
        for (int i = 0; i < lines.cols(); i++)
        {
            double[] vec = lines.get(0, i);
            double x1 = vec[0],
                    y1 = vec[1],
                    x2 = vec[2],
                    y2 = vec[3];

            Point start = new Point(x1, y1);
            Point end = new Point(x2, y2);

            Imgproc.line(matRgba, start, end, new Scalar(255, 0, 0), 3);
        }

        drawTmpToMRgba(null);

        //Cleanup
        Log.i(TAG, "lines:" + lines.cols());
        lines.release();
        lines = null;
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList()
    {
        return Collections.singletonList(openCvCameraView);
    }
    //----以上是 OpenCV 的功能----//

    //----以下是 Bluetooth 的功能----//
    /**初始化藍芽*/
    private void initBLE()
    {
        /**綁定Service
         * @see BluetoothLeService*/
        Intent bleService = new Intent(this, BluetoothLeService.class);
        bindService(bleService,mServiceConnection,BIND_AUTO_CREATE);
        /**設置廣播*/
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);//連接一個GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);//從GATT服務中斷開連接
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);//查找GATT服務
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);//從服務中接受(收)數據

        registerReceiver(mGattUpdateReceiver, intentFilter);
        if (mBluetoothLeService != null)
            mBluetoothLeService.connect(selectedDevice.getAddress());
    }
    /**初始化UI*/
    private void initUI()
    {
        tvAddress = findViewById(R.id.device_address);
        tvStatus = findViewById(R.id.connection_state);
        tvRespond = findViewById(R.id.data_value);
        tvAddress.setText(selectedDevice.getAddress());
        tvStatus.setText("未連線");
        tvRespond.setText("---");
    }
    /**藍芽已連接/已斷線資訊回傳*/
    private ServiceConnection mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize())
                finish();

            mBluetoothLeService.connect(selectedDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService.disconnect();
        }
    };
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            /**如果有連接*/
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))
            {
                Log.d(TAG, "藍芽已連線");
                tvStatus.setText("已連線");
            }
            /**如果沒有連接*/
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action))
                Log.d(TAG, "藍芽已斷開");

            /**找到GATT服務*/
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                Log.d(TAG, "已搜尋到GATT服務");
                List<BluetoothGattService> gattList =  mBluetoothLeService.getSupportedGattServices();
                displayGattAtLogCat(gattList);
            }
            /**接收來自藍芽傳回的資料*/
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                Log.d(TAG, "接收到藍芽資訊");
                byte[] getByteData = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                StringBuilder stringBuilder = new StringBuilder(getByteData.length);
                for (byte byteChar : getByteData)
                    stringBuilder.append(String.format("%02X ", byteChar));
                String stringData = new String(getByteData);
                Log.d(TAG, "String: "+stringData+"\n"
                        +"byte[]: "+BluetoothLeService.byteArrayToHexStr(getByteData));
                tvRespond.setText("String: "+stringData+"\n"
                        +"byte[]: "+BluetoothLeService.byteArrayToHexStr(getByteData));
                isLedOn = BluetoothLeService.byteArrayToHexStr(getByteData).equals("486173206F6E");
            }
        }
    };//onReceive

    /**將藍芽所有資訊顯示在Logcat*/
    private void displayGattAtLogCat(List<BluetoothGattService> gattList)
    {
        for (BluetoothGattService service : gattList)
        {
            Log.d(TAG, "Service: "+service.getUuid().toString());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics())
            {
                Log.d(TAG, "\tCharacteristic: "+characteristic.getUuid().toString()+" ,Properties: "+
                        mBluetoothLeService.getPropertiesTagArray(characteristic.getProperties()));
                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors())
                    Log.d(TAG, "\t\tDescriptor: "+descriptor.getUuid().toString());
            }
        }
    }

    /**關閉藍芽*/
    private void closeBluetooth()
    {
        if (mBluetoothLeService == null) return;
        mBluetoothLeService.disconnect();
        unbindService(mServiceConnection);
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        closeBluetooth();
    }
    //----以上是 Bluetooth 的功能----//
}