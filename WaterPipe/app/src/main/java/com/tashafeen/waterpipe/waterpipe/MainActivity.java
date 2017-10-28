package com.tashafeen.waterpipe.waterpipe;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

//import java.io.IOException;
import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import android.os.Handler;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

//import android.hardware.Sensor;
//import android.hardware.SensorEvent;
//import android.hardware.SensorEventListener;
//import android.hardware.SensorManager;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.tashafeen.waterpipe.waterpipe.cloud.CloudPublisherService;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;

import java.math.BigDecimal;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final long SENSOR_READ_INTERVAL_MS = TimeUnit.SECONDS.toMillis(20);

    private static final int USB_VENDOR_ID = 0x2341; // 9025
    private static final int USB_PRODUCT_ID = 0x0043; // 67 //0x0001;

    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialDevice;
    private String buffer = "";


    //if 0 => Training mode. if 1 => prediction mode using Simple model. if 2 => prediction mode using Tensorflow
    private int predictionMode;
    private static int BLOCK_COUNT =60 ;
    private PredictionInterface predictionInterface;
    private static List input_signal;


    private static final String LED1_PIN = "BCM17";
    private Handler mHandler = new Handler();
    private Gpio mLed1;


    /* Google Cloud IOT core */
    /**
     * Cutoff to consider a timestamp as valid. Some boards might take some time to update
     * their network time on the first time they boot, and we don't want to publish sensor data
     * with timestamps that we know are invalid. Sensor readings will be ignored until the
     * board's time (System.currentTimeMillis) is more recent than this constant.
     */
    private static final long INITIAL_VALID_TIMESTAMP;
    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016, 1, 1);
        INITIAL_VALID_TIMESTAMP = calendar.getTimeInMillis();
    }

    public static final String SENSOR_TYPE_SOUND_DETECTION = "sound";

    private CloudPublisherService mPublishService;
    private Looper mSensorLooper;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);


        //Tesnorflow
        predictionMode = 0;
        predictionInterface = new PredictionInterface(getApplicationContext());
        input_signal = new ArrayList();

        //LED
        PeripheralManagerService service = new PeripheralManagerService();
        try {
            mLed1 = service.openGpio(LED1_PIN);
            mLed1.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }

        //Managing USB Port
        usbManager = getSystemService(UsbManager.class);
        // Detach events are sent as a system-wide broadcast
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbDetachedReceiver, filter);

        //Integrate with Core IOT
        initializeServiceIfNeeded();

        // Start the thread that collects sensor data
        HandlerThread thread = new HandlerThread("CloudPublisherService");
        thread.start();
        mSensorLooper = thread.getLooper();

        final Handler sensorHandler = new Handler(mSensorLooper);
        sensorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    initializeServiceIfNeeded();
                    //connectToAvailableSensors();
                    collectContinuousSensors();
                } catch (Throwable t) {
                    Log.e(TAG, String.format(Locale.getDefault(),
                            "Cannot collect sensor data, will try again in %d ms",
                            SENSOR_READ_INTERVAL_MS), t);
                }
                sensorHandler.postDelayed(this, SENSOR_READ_INTERVAL_MS);
            }
            }, SENSOR_READ_INTERVAL_MS);

        //connectToAvailableSensors();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //USB
        unregisterReceiver(usbDetachedReceiver);
        stopUsbConnection();

        // unbind from Cloud Publisher service.
        if (mPublishService != null) {
            unbindService(mServiceConnection);
        }

        //LED
        if (mLed1 != null) {
            try {
                mLed1.setValue(false);
                mLed1.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

    }

    private void initializeServiceIfNeeded() {
        if (mPublishService == null) {
            try {
                // Bind to the service
                Intent intent = new Intent(this, CloudPublisherService.class);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            } catch (Throwable t) {
                Log.e(TAG, "Could not connect to the service, will try again later", t);
            }
        }
    }

    /**
     * Callback for service binding, passed to bindService()
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            CloudPublisherService.LocalBinder binder = (CloudPublisherService.LocalBinder) service;
            mPublishService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mPublishService = null;
        }
    };

    private void connectToAvailableSensors() {
        /*
        // Temperature and Pressure:
        if (mEnvironmentalSensor == null) {
            mEnvironmentalSensor = connectToBmx280();
        }

        if (mMotionDetectorSensor == null) {
            mMotionDetectorSensor = connectToMotionDetector();
        }
        */
    }

    private void collectContinuousSensors() {
        if (mPublishService != null) {
            List<SensorData> sensorsData = new ArrayList<>();
            //addBmx280Readings(sensorsData);
            Log.d(TAG, "collected continuous sensor data: " + sensorsData);
            mPublishService.logSensorData(sensorsData);
        }
    }

    private void collectSensorOnChange(String type, float sensorReading) {
        if (mPublishService != null) {
            //Log.d(TAG, "On change " + type + ": " + sensorReading);
            long now = System.currentTimeMillis();
            if (now >= INITIAL_VALID_TIMESTAMP) {
                mPublishService.logSensorDataOnChange(new SensorData(now, type, sensorReading));
            } else {
                Log.i(TAG, "Ignoring sensor readings because timestamp is invalid. " +
                        "Please, set the device's date/time");
            }
        }
    }









    private UsbSerialInterface.UsbReadCallback callback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] data) {
            try {
                    String dataUtf8 = new String(data, "UTF-8");
                    buffer += dataUtf8;
                    int index;
                    while ((index = buffer.indexOf('\n')) != -1) {
                    final String dataStr = buffer.substring(0, index + 1).trim();
                    buffer = buffer.length() == index ? "" : buffer.substring(index + 1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onSerialDataReceived(dataStr);
                        }
                    });

                    dataStr.trim();

                    if(!dataStr.isEmpty())
                    {
                        float datafloat ;
                        try {
                            datafloat = Float.valueOf(dataStr);
                            if( datafloat < 1000)
                            {
                                if ( predictionMode  == 0 )// training mode
                                    collectSensorOnChange(SENSOR_TYPE_SOUND_DETECTION , datafloat );
                                else if ( predictionMode  == 1 )// Prediction  mode - count model
                                    activityPredictionSimpleModel(datafloat);
                                else if ( predictionMode  == 2 )// Prediction  mode - tesnorflow
                                    activityPrediction(datafloat);
                            }
                        } catch(NumberFormatException e) {
                            Log.e(TAG, "Error receiving USB data", e);
                        }
                    }

                }


            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Error receiving USB data", e);
            }
        }
    };

    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "USB device detached");
                    stopUsbConnection();
                }
            }
        }
    };



    @Override
    protected void onResume() {
        super.onResume();
        startUsbConnection();
    }


    private void startUsbConnection() {
        Map<String, UsbDevice> connectedDevices = usbManager.getDeviceList();

        if (!connectedDevices.isEmpty()) {
            for (UsbDevice device : connectedDevices.values()) {
                if (device.getVendorId() == USB_VENDOR_ID && device.getProductId() == USB_PRODUCT_ID) {
                    Log.i(TAG, "Device found: " + device.getDeviceName());
                    startSerialConnection(device);
                    return;
                }
            }
        }
        Log.w(TAG, "Could not start USB connection - No devices found");
    }

    private void startSerialConnection(UsbDevice device) {
        Log.i(TAG, "Ready to open USB device connection");
        connection = usbManager.openDevice(device);
        serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection);
        if (serialDevice != null) {
            if (serialDevice.open()) {
                serialDevice.setBaudRate(115200);
                serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
                serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1);
                serialDevice.setParity(UsbSerialInterface.PARITY_NONE);
                serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                serialDevice.read(callback);
                Log.i(TAG, "Serial connection opened");
            } else {
                Log.w(TAG, "Cannot open serial connection");
            }
        } else {
            Log.w(TAG, "Could not create Usb Serial Device");
        }
    }

    private void onSerialDataReceived(String data) {
        // Add whatever you want here
        Log.i(TAG, "Serial data received: " + data);
    }

    private void stopUsbConnection() {
        try {
            if (serialDevice != null) {
                serialDevice.close();
            }

            if (connection != null) {
                connection.close();
            }
        } finally {
            serialDevice = null;
            connection = null;
        }
    }

    private void activityPredictionSimpleModel(float count)
    {
        try {
            if ( count > 1 ) {
                mLed1.setValue(true);
            } else {
                mLed1.setValue(false);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void activityPrediction(float soundValue)
    {
        //if(x.size() == N_SAMPLES && y.size() == N_SAMPLES && z.size() == N_SAMPLES)
        {
            // Copy all x,y and z values to one array of shape N_SAMPLES*3
            //input_signal.addAll(x); input_signal.addAll(y); input_signal.addAll(z);
            input_signal.add(soundValue);

            // Perform inference using Tensorflow
            float[] results = predictionInterface.getActivityProb(toFloatArray(input_signal));

            //result will be true or false. if true, turn the LED on
            try {
                if (round(results[0], 2) > 0) {
                    mLed1.setValue(true);
                } else {
                    mLed1.setValue(false);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }

            // Clear all the values
            input_signal.clear();
        }
    }



    private float[] toFloatArray(List list)
    {
        //int i = 0;
        int arraySize = list.size() ;
        float[] array = new float[ arraySize ];

        //for (Float f : list) {
        for(int i=0 ; i < arraySize ; i++){
            Float f = (Float)list.get(i);
            array[i++] = (f != null ? f : Float.NaN);
        }
        return array;
    }

    public static float round(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }
}
