package fr.stephane.hexapodvorpalremote;

import android.app.ActionBar;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class HexapodRemoteControl extends AppCompatActivity {
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    /**
     * list of button id for font awesome
     */
    private static final int FONT_AWESOME_BUTTON_ID[] = {
            R.id.button_down,
            R.id.button_left,
            R.id.button_up,
            R.id.button_right,
            R.id.button_middle
    };

    private final static String TAG = HexapodRemoteControl.class.getSimpleName();


    public final static UUID UUID_HEXAPOD_SERVICE =
            UUID.fromString("d126db57-3b24-4077-bf38-759927bacc54");
    public final static UUID UUID_HEXAPOD_CHARACTERISTIC =
            UUID.fromString("03cd0bcf-5c00-49eb-9d2a-d7ed2791d762");

    private String mDeviceName;
    private String mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;

    private String cmdPrefix = "W1";
    private Button lastMod = null;
    private BluetoothGattCharacteristic bleGattCharacteristic = null;
    private boolean isMetaMode = true;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hexapod_remote_control);
        Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/fontawesome-webfont.ttf");
        for (int id : FONT_AWESOME_BUTTON_ID) {
            Button button = findViewById(id);
            button.setTypeface(typeface);
        }

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mDeviceName);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        //delayedHide(100);

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private String computeCmd(final int id) {
        switch (id) {
            case R.id.button_up:
                return "f";
            case R.id.button_down:
                return "b";
            case R.id.button_middle:
                return "w";
            case R.id.button_left:
                return "l";
            case R.id.button_right:
                return "r";
        }
        return "s";
    }

    private void getCharacteristic() {
        if (mBluetoothLeService != null) {
            if (bleGattCharacteristic == null) {
                List<BluetoothGattService> supportedGattServices = mBluetoothLeService.getSupportedGattServices();
                for (BluetoothGattService service : supportedGattServices) {
                    Log.i(TAG, service.getUuid().toString());
                    if (UUID_HEXAPOD_SERVICE.equals(service.getUuid())) {
                        for (BluetoothGattCharacteristic bluetoothGattCharacteristic : service.getCharacteristics()) {
                            Log.i(TAG, ">>" + bluetoothGattCharacteristic.getUuid());
                            if (UUID_HEXAPOD_CHARACTERISTIC.equals(bluetoothGattCharacteristic.getUuid())) {
                                Log.i(TAG, "found!");
                                bleGattCharacteristic = bluetoothGattCharacteristic;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public void onMoveBtnClick(View v) {
        final String cmd = cmdPrefix + computeCmd(v.getId());
        if (mBluetoothLeService == null) {
            Toast.makeText(this, "not connected " + cmd, Toast.LENGTH_LONG).show();
            return;
        }
        isMetaMode = false;

        // Toast.makeText(this, "Clicked on Button " + cmd, Toast.LENGTH_LONG).show();
        getCharacteristic();
        if (bleGattCharacteristic != null) {
            bleGattCharacteristic.setValue(cmd);
            mBluetoothLeService.writeCharacteristic(bleGattCharacteristic);
            //Toast.makeText(this, "sent " + cmd, Toast.LENGTH_LONG).show();
        }
    }

    String convertMetaMod(int id) {
        switch (id) {
            case R.id.record_1:
                return "M2";
            case R.id.record_2:
                return "M1";
            case R.id.record_3:
                return "M5";
            case R.id.record_4:
                return "M3";
            case R.id.record_5:
                return "M0";
        }
        return null;
    }

    public void onMetaModBtnClick(View v) {
        if (mBluetoothLeService == null) {
            Toast.makeText(this, "not connected ", Toast.LENGTH_LONG).show();
            return;
        }
        isMetaMode = true;
        String cmd = convertMetaMod(v.getId());
        /*cmdPrefix = "W1";
        if (lastMod != null) {
            lastMod.setBackgroundColor(Color.TRANSPARENT);
        }*/

        getCharacteristic();
        if (bleGattCharacteristic != null) {
            bleGattCharacteristic.setValue(cmd);
            mBluetoothLeService.writeCharacteristic(bleGattCharacteristic);
        }
    }

    public void onModBtnClick(View v) {
        Button button = findViewById(v.getId());
        if (lastMod != null) {
            lastMod.setBackgroundColor(Color.TRANSPARENT);
        }
        lastMod = button;
        button.setBackgroundColor(Color.BLUE);
        cmdPrefix = button.getText().toString();
    }

    public void onStopMotionBtnClick(View v) {
        if (mBluetoothLeService == null) {
            Toast.makeText(this, "not connected ", Toast.LENGTH_LONG).show();
            return;
        }
        String cmd = isMetaMode ? "M6" : cmdPrefix + "s";

        getCharacteristic();
        if (bleGattCharacteristic != null) {
            bleGattCharacteristic.setValue(cmd);
            mBluetoothLeService.writeCharacteristic(bleGattCharacteristic);
        }
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Toast.makeText(HexapodRemoteControl.this, "connected ", Toast.LENGTH_LONG).show();
/*                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();*/
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(HexapodRemoteControl.this, "disconnected ", Toast.LENGTH_LONG).show();

               /* mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();*/
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //  displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connection:
                if(mBluetoothLeService.getmConnectionState()==BluetoothLeService.STATE_DISCONNECTED){
                    mBluetoothLeService.connect(mDeviceAddress);
                }
                break;
        }

        return true;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
