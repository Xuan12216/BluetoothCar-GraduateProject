package com.example.bluetoothcarver1;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.example.bluetoothcarver1.Module.Enitiy.ScannedData;
import com.example.bluetoothcarver1.Module.Service.BluetoothLeService;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SelfControl extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    public static final String INTENT_KEY = "GET_DEVICE";
    private BluetoothLeService mBluetoothLeService;
    private ScannedData selectedDevice;
    private TextView tvAddress,tvStatus,tvRespond;
    private Button btn1,btn2,btn3,btn4,btn5,btn6,btn7,btn8,btn9;

    public SelfControl() { Log.i(TAG, "Instantiated new " + this.getClass()); }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_self_control);

        btn1 = (Button) findViewById(R.id.button1);
        btn1.setOnClickListener(onClickListener);
        btn2 = (Button) findViewById(R.id.button2);
        btn2.setOnClickListener(onClickListener);
        btn3 = (Button) findViewById(R.id.button3);
        btn3.setOnClickListener(onClickListener);
        btn4 = (Button) findViewById(R.id.button4);
        btn4.setOnClickListener(onClickListener);
        btn5 = (Button) findViewById(R.id.button5);
        btn5.setOnClickListener(onClickListener);
        btn6 = (Button) findViewById(R.id.button6);
        btn6.setOnClickListener(onClickListener);
        btn7 = (Button) findViewById(R.id.button7);
        btn7.setOnClickListener(onClickListener);
        btn8 = (Button) findViewById(R.id.button8);
        btn8.setOnClickListener(onClickListener);
        btn9 = (Button) findViewById(R.id.button9);
        btn9.setOnClickListener(onClickListener);

        selectedDevice = (ScannedData) getIntent().getSerializableExtra(INTENT_KEY);
        initBLE();
        initUI();
    }

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
        if (mBluetoothLeService != null) mBluetoothLeService.connect(selectedDevice.getAddress());
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

    public View.OnClickListener onClickListener = new View.OnClickListener()
    {
        String sendData1,sendData2,sendData3;
        @Override
        public void onClick(View view)
        {
            switch (view.getId())
            {
                case R.id.button1:
                    sendData1 = "SRV2000155015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button2:
                    sendData1 = "SRV1500155015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button3:
                    sendData1 = "SRV1000155015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button4:
                    sendData1 = "SRV2000150015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button5:
                    sendData1 = "SRV1500150015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button6:
                    sendData1 = "SRV1000150015001500#";
                    tvRespond.setText(sendData1);
                    mBluetoothLeService.send(sendData1.getBytes());
                    break;
                case R.id.button7:
                    sendData1 = "SRV2000148015001500#";
                    sendData2 = "SRV2000147015001500#";
                    sendData3 = "SRV2000140015001500#";
                    tvRespond.setText(sendData3);

                    mBluetoothLeService.send(sendData1.getBytes());/**sendData*/

                    try {
                        TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData2.getBytes());/**sendData2*/

                    try {TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData3.getBytes());/**sendData3*/
                    break;
                case R.id.button8:
                    sendData1 = "SRV1500148015001500#";
                    sendData2 = "SRV1500147015001500#";
                    sendData3 = "SRV1500140015001500#";
                    tvRespond.setText(sendData3);

                    mBluetoothLeService.send(sendData1.getBytes());/**sendData*/

                    try {TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData2.getBytes());/**sendData2*/

                    try {TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData3.getBytes());/**sendData3*/
                    break;
                case R.id.button9:
                    sendData1 = "SRV1000148015001500#";
                    sendData2 = "SRV1000147015001500#";
                    sendData3 = "SRV1000140015001500#";
                    tvRespond.setText(sendData3);

                    mBluetoothLeService.send(sendData1.getBytes());/**sendData*/

                    try {TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData2.getBytes());/**sendData2*/

                    try {TimeUnit.MILLISECONDS.sleep(100);}/**Wait 200 milliseconds*/
                    catch (InterruptedException e) {e.printStackTrace();}

                    mBluetoothLeService.send(sendData3.getBytes());/**sendData3*/
                    break;
            }
        }
    };
}