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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
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

public class OpenCvControl extends CameraActivity
{
    //Bluetooth
    private static final String TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;

    //OpenCV
    //Height of image frame
    private int height;
    //Width of image frame
    private int width;
    //Working matrices
    private Mat matRgba;
    Mat imgHSV_red, imgHSV_white;
    int isFirstTime=0;
    String[] Command1 = new String[]{"1000","1100","1200","1300","1400", // Right, 數字越小轉彎幅度越大
                                    "1500",                             // Middle
                                    "1600","1700","1800","1900","2000"};// Left , 數字越大轉彎幅度越大
    String[] Command2 = new String[]{"1546","1500"};// 1546 = 向前走, 1500 = 停
    String   Command3 = "15001500#";//無用的指令
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
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCvCameraView.enableView();
                break;
                default:
                    super.onManagerConnected(status);
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_open_cv_control);

        isFirstTime = 0;
        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        initBLE();

        // 实现绑定和添加事件监听
        openCvCameraView = findViewById(R.id.surface_view);
        openCvCameraView.setCvCameraViewListener(cvCameraViewListener);
        openCvCameraView.setFocusable(true);
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
            imgHSV_red = new Mat(width,height,CvType.CV_8UC4);
            imgHSV_white = new Mat(width,height,CvType.CV_8UC4);
            matRgba = new Mat(height, width, CvType.CV_8UC4);
        }

        @Override
        public void onCameraViewStopped()
        {
            imgHSV_red.release();
            imgHSV_white.release();
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
            //RedColor Mat
            Mat RedColor = new Mat(height, width, CvType.CV_8UC4);
            //RedColor submat, row start = 上面 , row end = 下面 , col start = 左边 , col end = 右边
            matRgba.submat(50, height/2+50, 20, width/2).copyTo(RedColor.submat(50, height/2+50, 20, width/2));
            //convert color RGB to HSV_FULL and save the convert data to imgHSV MAT
            Imgproc.cvtColor(RedColor, imgHSV_red, Imgproc.COLOR_RGB2HSV_FULL);
            //set the color range , can scan red color object only
            Core.inRange(imgHSV_red, new Scalar(0,100,60), new Scalar(5, 255, 255), imgHSV_red);
            //進行腐蚀，使圖像的襍訊更少，size 越小，腐蚀的单位越小，图片越接近原图
            Imgproc.erode(imgHSV_red, imgHSV_red, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Imgproc.dilate(imgHSV_red, imgHSV_red, new Mat());
            //find contours
            List<MatOfPoint> contours_red = new ArrayList<MatOfPoint>();
            Imgproc.findContours(imgHSV_red, contours_red, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            //===================================================================================================
            //WhiteColor Mat
            Mat WhiteColor = new Mat(height, width, CvType.CV_8UC4);
            matRgba.submat(height - 120, height - 20, 20, width - 20).copyTo(WhiteColor.submat(height - 120, height - 20, 20, width - 20));
            //convert color BRG to RGB and save the convert data to imgRgba MAT
            Imgproc.cvtColor(WhiteColor, imgHSV_white, Imgproc.COLOR_BGR2RGB);
            //set the color range , can scan white color object only
            Core.inRange(imgHSV_white, new Scalar(130,130,130), new Scalar(255, 255, 255), imgHSV_white);
            // size 越小，腐蚀的单位越小，图片越接近原图
            Imgproc.erode(imgHSV_white,imgHSV_white, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Imgproc.dilate(imgHSV_white,imgHSV_white, new Mat());
            //find contours
            List<MatOfPoint> contours_white = new ArrayList<MatOfPoint>();
            Imgproc.findContours(imgHSV_white, contours_white, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

            //Draw Rectangle and Circle
            DrawRectangleAndCircle(matRgba);
            //Goto Bluetooth Control_Red Function
            findColor(contours_red,contours_white,matRgba);
            //show the image data
            return matRgba;
        }
    };

    private void DrawRectangleAndCircle(Mat matRgba)
    {
        //draw rectangle
        Imgproc.rectangle(matRgba, new Rect(20, height - 120, width - 40, 100), new Scalar(0, 0, 255), 3);
        Imgproc.rectangle(matRgba, new Rect(20, 50, (width/2)-20, height/2), new Scalar(255, 0, 0), 3);
        //draw circle
        Imgproc.circle(matRgba, new Point(width / 2, height - 70), 3, new Scalar(255, 0, 0), 5);
    }

    private void findColor(List<MatOfPoint> contours_red, List<MatOfPoint> contours_white, Mat matRgba)
    {
        if(isFirstTime == 0) // if First Time Run the Program, Give Some times to prepare
            try {Thread.sleep(1000);}
            catch (InterruptedException e) {e.printStackTrace();}
            isFirstTime = 1;

        if(contours_red.size() != 0) // Red Color Detect
            BluControl("Red Color,Stop",5,1);
        else if(contours_red.size() == 0 && contours_white.size() != 0) // White Color Detect
        {
            // find appropriate bounding rectangles
            for (MatOfPoint contour : contours_white)
            {
                MatOfPoint2f areaPoints = new MatOfPoint2f(contour.toArray());
                RotatedRect boundingRect = Imgproc.minAreaRect(areaPoints);
                double rectangleArea = boundingRect.size.area();
                // test min src area in pixels
                if (rectangleArea >= 5000 && rectangleArea <= 40000)
                {
                    Imgproc.putText(matRgba, "Area:" + rectangleArea, new org.opencv.core.Point(width/2, 90), 0, 0.9, new Scalar(255, 255, 0), 2);
                    Point[] vertices = new Point[4];
                    boundingRect.points(vertices);
                    List<MatOfPoint> boxContours = new ArrayList<>();
                    boxContours.add(new MatOfPoint(vertices));
                    Imgproc.drawContours(matRgba, boxContours, 0, new Scalar(0, 0, 255), 3);
                    Mat result = new Mat();
                    Imgproc.boxPoints(boundingRect, result);
                    //draw line
                    Imgproc.line(matRgba, new Point(width / 2, height - 70), new Point((int) boundingRect.center.x, (int) boundingRect.center.y), new Scalar(0, 255, 0), 3);
                    int distance = (((int) boundingRect.center.x) - (width / 2));
                    Imgproc.putText(matRgba, "Distance:" + distance, new org.opencv.core.Point(width/2, 30), 0, 0.9, new Scalar(255, 255, 0), 2);
                    //left / right or middle
                    if (boundingRect.center.x > width / 2 + 50) // Right
                    {
                        if (distance >= 51 && distance <= 100)
                            BluControl("Right_1",3,0);
                        else if (distance >= 101 && distance <= 150)
                            BluControl("Right_2",2,0);
                        else if (distance >= 151 && distance <= 200)
                            BluControl("Right_3",1,0);
                        else if(distance >= 201)
                            BluControl("Right_4",0,0);
                    }
                    else if (boundingRect.center.x < width / 2 - 50) // Left
                    {
                        if (distance >= -100 && distance <= -51)
                            BluControl("Left_1",7,0);
                        else if (distance >= -150 && distance <= -101)
                            BluControl("Left_2",8,0);
                        else if (distance >= -200 && distance <= -151)
                            BluControl("Left_3",9,0);
                        else if (distance <= -201)
                            BluControl("Left_4",10,0);
                    }
                    else if(boundingRect.center.x >= width / 2 - 50 && boundingRect.center.x <= width / 2 + 50 ) // Middle
                        BluControl("Middle",5,0);
                }
                else if(rectangleArea > 40000)
                    BluControl("No Color Detect",5,1);
                else
                    BluControl("No Color Detect",5,1);
            }
        }
        else if(contours_red.size() == 0 && contours_white.size() == 0) // No Color Detect
            BluControl("No Color Detect",5,1);
    }

    private void BluControl(String Direction, int index1, int index2)
    {
        Imgproc.putText(matRgba, Direction, new org.opencv.core.Point(20, 30), 0, 0.9, new Scalar(255, 255, 0), 2);
        String sendData = "SRV"+ Command1[index1]+Command2[index2]+Command3;
        mBluetoothLeService.send(sendData.getBytes());
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

        //registerReceiver(mGattUpdateReceiver, intentFilter);
        if (mBluetoothLeService != null)
            mBluetoothLeService.connect(selectedDevice.getAddress());

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
                Log.d(TAG, "藍芽已連線");
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