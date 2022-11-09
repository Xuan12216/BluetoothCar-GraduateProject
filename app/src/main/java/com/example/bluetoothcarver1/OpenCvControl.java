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
import android.view.Window;
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
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenCvControl extends CameraActivity
{
    //Bluetooth
    private static final String TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    private TextView tvAddress,tvStatus,tvRespond;

    //OpenCV
    //Current screen orientation
    public static int orientation;
    //Height of image frame
    private int height;
    //Width of image frame
    private int width;
    //Working matrices
    private Mat matRgba;
    Mat imgHSV, imgRgba;
    int count=0;
    //camera bridge
    private CameraBridgeViewBase openCvCameraView;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_open_cv_control);

        count=0;
        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        initBLE();
        initUI();

        // 实现绑定和添加事件监听
        openCvCameraView = findViewById(R.id.hough_activity_surface_view);
        openCvCameraView.setCvCameraViewListener(cvCameraViewListener);
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
            imgHSV = new Mat(width_scr,height_scr,CvType.CV_8UC4);
            imgRgba = new Mat(width_scr,height_scr,CvType.CV_8UC4);
            width = width_scr;
            height = height_scr;
            matRgba = new Mat(height, width, CvType.CV_8UC4);
        }

        @Override
        public void onCameraViewStopped()
        {
            imgHSV.release();
            imgRgba.release();
            matRgba.release();
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
            Mat roi = new Mat(height, width, CvType.CV_8UC4);
            //traffic
            Mat traffic = new Mat(height, width, CvType.CV_8UC4);
            matRgba.submat(height - 200, height - 50, 20, width - 20).copyTo(roi.submat(height - 200, height - 50, 20, width - 20));
            //traffic
            matRgba.submat(height - 200, height - 50, 20, width - 20).copyTo(traffic.submat(height - 200, height - 50, 20, width - 20));
            //convert color BRG to RGB and save the convert data to imgRgba MAT
            Imgproc.cvtColor(roi, imgRgba, Imgproc.COLOR_BGR2RGB);
            //traffic
            Imgproc.cvtColor(traffic, imgHSV, Imgproc.COLOR_RGB2HSV_FULL);
            //set the color range , can scan black color object only
            Core.inRange(imgRgba, new Scalar(130, 130, 130), new Scalar(255, 255, 255), imgRgba);
            //traffic
            Core.inRange(imgHSV, new Scalar(0,100,60), new Scalar(5, 255, 255), imgHSV);
            // size 越小，腐蚀的单位越小，图片越接近原图
            Imgproc.erode(imgRgba, imgRgba, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Imgproc.dilate(imgRgba, imgRgba, new Mat());
            //traffic
            Imgproc.erode(imgHSV, imgHSV, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Imgproc.dilate(imgHSV, imgHSV, new Mat());
            //draw rectangle
            Imgproc.rectangle(matRgba, new Rect(10, height - 210, width - 20, 170), new Scalar(255, 0, 0), 10);
            //draw circle
            Imgproc.circle(matRgba, new Point(width / 2, height - 125), 5, new Scalar(255, 0, 0), 20);
            //find contours
            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Imgproc.findContours(imgRgba, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
            //traffic
            List<MatOfPoint> contours1 = new ArrayList<MatOfPoint>();
            Imgproc.findContours(imgHSV, contours1, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            if(count==0)
            {
                try {Thread.sleep(1000);}
                catch (InterruptedException e) {e.printStackTrace();}
                count+=1;
            }
            if(contours1.size()>0)
            {
                Imgproc.putText(matRgba, "Red Color,Stop", new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 10);
                String sendData1 = "SRV1500150015001500#";
                mBluetoothLeService.send(sendData1.getBytes());
            }
            else if(contours.size()>0 && contours1.size()<=0)
            {
                // find appropriate bounding rectangles
                for (MatOfPoint contour : contours)
                {
                    MatOfPoint2f areaPoints = new MatOfPoint2f(contour.toArray());
                    RotatedRect boundingRect = Imgproc.minAreaRect(areaPoints);
                    double rectangleArea = boundingRect.size.area();
                    // test min src area in pixels
                    if (rectangleArea > 10000 && rectangleArea < 500000) //400000
                    {
                        Point[] vertices = new Point[4];
                        boundingRect.points(vertices);
                        List<MatOfPoint> boxContours = new ArrayList<>();
                        boxContours.add(new MatOfPoint(vertices));
                        Imgproc.drawContours(matRgba, boxContours, 0, new Scalar(0, 0, 255), 10);
                        Mat result = new Mat();
                        Imgproc.boxPoints(boundingRect, result);
                        //draw line
                        Imgproc.line(matRgba, new Point(width / 2, height - 125), new Point((int) boundingRect.center.x, (int) boundingRect.center.y), new Scalar(0, 255, 0), 5);
                        int distance = (((int) boundingRect.center.x) - (width / 2));
                        Imgproc.putText(matRgba, "Distance:" + distance, new org.opencv.core.Point(500, 100), 0, 2, new Scalar(255, 255, 0), 5);
                        //left / right or middle
                        String sendData1;
                        if (boundingRect.center.x > width / 2 + 100)
                        {
                            if (distance < 200)
                            {
                                Imgproc.putText(matRgba, "right_1:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1400154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance < 300)
                            {
                                Imgproc.putText(matRgba, "right_2:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1300154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance < 400)
                            {
                                Imgproc.putText(matRgba, "right_3:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1200154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance < 500)
                            {
                                Imgproc.putText(matRgba, "right_4:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1100154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance < 600)
                            {
                                Imgproc.putText(matRgba, "right_5:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1000154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                        }
                        else if (boundingRect.center.x < width / 2 - 100)
                        {
                            if (distance > -200)
                            {
                                Imgproc.putText(matRgba, "left_1:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1600154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance > -300)
                            {
                                Imgproc.putText(matRgba, "left_2:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1700154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance > -400)
                            {
                                Imgproc.putText(matRgba, "left_3:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1800154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance > -500)
                            {
                                Imgproc.putText(matRgba, "left_4:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV1900154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                            else if (distance > -600)
                            {
                                Imgproc.putText(matRgba, "left_5:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 5);
                                sendData1 = "SRV2000154515001500#";
                                mBluetoothLeService.send(sendData1.getBytes());
                            }
                        }
                        else if(boundingRect.center.x > width / 2 - 100 && boundingRect.center.x < width / 2 + 100)
                        {
                            Imgproc.putText(matRgba, "middle:" + String.format("%02.0f", boundingRect.center.x), new org.opencv.core.Point(0, 100), 0, 2, new Scalar(255, 255, 0), 10);
                            sendData1 = "SRV1500154515001500#";
                            mBluetoothLeService.send(sendData1.getBytes());
                        }
                    }
                }
            }
            //show the image data
            return matRgba;
        }
    };

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

        //registerReceiver(mGattUpdateReceiver, intentFilter);
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
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
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