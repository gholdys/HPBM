package hpbm.app.ble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.WindowManager;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import hpbm.app.R;
import hpbm.app.core.Communicator;
import hpbm.app.core.MessageInterpreter;
import hpbm.app.core.HPBMData;
import hpbm.app.core.HPBMDataHandler;
import hpbm.app.core.HPBMDevicesDiscoveryHandler;

public class BLECommunicator implements Communicator {

    private static final String TAG = BLECommunicator.class.getSimpleName();
    private static final int PERMISSION_REQUEST_CODE_COARSE_LOCATION = 1;
    private static final int ACTIVITY_REQUEST_CODE_ENABLE_BLUETOOTH = 1;
    private static final int ACTIVITY_REQUEST_CODE_ENABLE_LOCATION = 2;

    // Service Constants
    private static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private static final int TX_MAX_CHARACTERS = 20;
    private static final int SCAN_TIMEOUT = 2000; // [ms]

    private BluetoothLeScanner mScanner;
    private BleManager mBleManager;
    private BluetoothGattService mUartService;
    private String mDeviceAddress;
    private final LinkedList<Runnable> actionChain = new LinkedList<>();
    private final MessageInterpreter messageInterpreter;
    private HPBMDataHandler dataHandler;

    public BLECommunicator(MessageInterpreter messageInterpreter ) {
        this.messageInterpreter = messageInterpreter;
    }

    public void setDataHandler(HPBMDataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    public HPBMDataHandler getDataHandler() {
        return dataHandler;
    }

    @Override
    public boolean onRequestPermissionsResult( final Activity hostActivity, int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");
                    runNextActionInChain();
                } else {
                    AlertDialog dialog = new AlertDialog.Builder(hostActivity)
                        .setTitle("Unable to continue")
                        .setMessage("Since location access has not been granted, this application will now exit. Bye!")
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                hostActivity.finish();
                            }
                        })
                        .show();
                    keepDialogOnOrientationChanges(dialog);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onActivityResult( Activity hostActivity, int requestCode, int resultCode, Intent intent) {
        if ( requestCode == ACTIVITY_REQUEST_CODE_ENABLE_BLUETOOTH && resultCode == Activity.RESULT_OK ) {
            runNextActionInChain();
            return true;
        } else if ( requestCode == ACTIVITY_REQUEST_CODE_ENABLE_LOCATION ) {
            setupLocationServiceForScanning(hostActivity);
            return true;
        }
        return false;
    }

    @Override
    public void listAvailableDevices( final Activity hostActivity, final HPBMDevicesDiscoveryHandler handler) {
        clearActionChain();

        addActionToChain(new Runnable() {
            @Override
            public void run() {
                setupBleManager( hostActivity );
            }
        });

        addActionToChain(new Runnable() {
            @Override
            public void run() {
                setupBluetooth( hostActivity );
            }
        });

        addActionToChain(new Runnable() {
            @Override
            public void run() {
                setupLocationServiceForScanning( hostActivity );
            }
        });

        addActionToChain(new Runnable() {
            @Override
            public void run() {
                setupLocationPermissions( hostActivity );
            }
        });

        addActionToChain(new Runnable() {
            @Override
            public void run() {
                startScan( hostActivity, handler );
            }
        });

        startActionChain();
    }

    @Override
    public boolean connect( Activity hostActivity, String hpbmDeviceAddress ) {
        mDeviceAddress = hpbmDeviceAddress;
//        readSettings();
//        this.dataHandler = handler;
//        boolean runFullSetup = true;
//
//        if ( mDeviceAddress != null ) {
//            Log.d(TAG, "Found a device address in the settings. Will try to connect to it.");
            mBleManager = new BleManager(hostActivity, createBleManagerListenerImpl() );
            return mBleManager.connect(hostActivity, mDeviceAddress);
//            if ( connected ) {
//                Log.d( TAG, "Connection successful! No need to do the full setup procedure.");
//                runFullSetup = false;
//            } else {
//                Log.d( TAG, "Connection attempt failed! Reverting to the full setup procedure.");
//            }
//        } else {
//            Log.d(TAG, "No valid device address found in settings. Starting the full setup procedure.");
//        }
//
//        if ( runFullSetup ) {
//            clearActionChain();
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    setupBleManager();
//                }
//            });
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    setupBluetooth();
//                }
//            });
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    tryToConnect();
//                }
//            });
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    setupLocationServiceForScanning();
//                }
//            });
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    setupLocationPermissions();
//                }
//            });
//
//            addActionToChain(new Runnable() {
//                @Override
//                public void run() {
//                    startScan();
//                }
//            });
//
//            startActionChain();
//        }
    }

    @Override
    public boolean disconnect( Activity hostActivity ) {
        Log.d(TAG, "Disconnecting...");
        if ( mBleManager != null ) {
            mBleManager.close();
        }
        saveSettings( hostActivity );
        return true;
    }

    @Override
    public boolean sendRefillToMessage(Activity hostActivity, float amount) {
        String message = messageInterpreter.createRefillToMessage( amount );
        return sendData(message);
    }

    @Override
    public boolean sendRefillWithMessage(Activity hostActivity, float amount) {
        String message = messageInterpreter.createRefillWithMessage( amount );
        return sendData(message);
    }

    @Override
    public boolean sendResetMessage( Activity hostActivity ) {
        String message = messageInterpreter.createResetMessage();
        return sendData(message);
    }


    // *************************************************
    // ************** PRIVATE METHODS ******************
    // *************************************************
    private void setupBleManager( Activity hostActivity ) {
        if ( mBleManager == null ) {
            Log.d(TAG, "Setup BLE Manager");
            mBleManager = new BleManager(hostActivity, createBleManagerListenerImpl());
        }
        runNextActionInChain();
    }

    private void setupBluetooth( Activity hostActivity ) {
        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(hostActivity.getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.dialog_error_no_bluetooth;
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                Log.d(TAG, "Launch settings dialog to enable Bluetooth");
                // if not enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                hostActivity.startActivityForResult(enableBtIntent, ACTIVITY_REQUEST_CODE_ENABLE_BLUETOOTH);
                // execution will continue at onActivityResult()
                return;
            }
        }

        if (errorMessageId > 0) {
            Log.d(TAG, "Bluetooth Error: " + hostActivity.getApplicationContext().getText(errorMessageId));
            AlertDialog dialog = new AlertDialog.Builder(hostActivity)
                    .setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            keepDialogOnOrientationChanges(dialog);
        } else {
            runNextActionInChain();
        }
    }

    private void tryToConnect( Activity hostActivity ) {
        if ( mDeviceAddress != null && mBleManager.isAdapterEnabled() ) {
            boolean connected = mBleManager.connect(hostActivity, mDeviceAddress );
            if ( connected ) {
                Log.d(TAG, "Connection to device at \"" + mDeviceAddress + "\" established. Breaking setup procedure.");
                clearActionChain();
                return;
            }
        }
        runNextActionInChain();
    }

    private void setupLocationServiceForScanning( final Activity hostActivity ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            Log.d(TAG, "Checking location service...");
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(hostActivity.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                Log.w(TAG, e);
            }

            if ( locationMode == Settings.Secure.LOCATION_MODE_OFF ) {
                Log.d(TAG, "Location service is off. Alert the user.");
                AlertDialog dialog = new AlertDialog.Builder(hostActivity)
                        .setMessage(R.string.dialog_error_nolocationservices_requiredforscan_marshmallow)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                hostActivity.startActivityForResult( new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), ACTIVITY_REQUEST_CODE_ENABLE_LOCATION );
                            }
                        })
                        .show();
                keepDialogOnOrientationChanges(dialog);
            } else {
                Log.d(TAG, "Location service is on. Continue.");
                runNextActionInChain();
            }
        } else {
            runNextActionInChain();
        }
    }

    private void setupLocationPermissions( final Activity hostActivity ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "Checking location permissions...");
            if ( hostActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
                Log.d(TAG, "Location permissions not granted. Alert the user.");
                AlertDialog dialog = new AlertDialog.Builder(hostActivity)
                        .setTitle(R.string.location_access_request_title)
                        .setMessage(R.string.location_access_request_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                ActivityCompat.requestPermissions(hostActivity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE_COARSE_LOCATION);
                            }
                        })
                        .show();
                keepDialogOnOrientationChanges(dialog);
            } else {
                Log.d(TAG, "Location permissions granted. Continue.");
                runNextActionInChain();
            }
        } else {
            runNextActionInChain();
        }
    }

    private void startScan( Activity hostActivity, final HPBMDevicesDiscoveryHandler handler ) {
        Log.d(TAG, "Start scanning");

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(hostActivity.getApplicationContext());
        if (BleUtils.getBleStatus(hostActivity) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "Failed to start device scan! Bluetooth adapter not initialized or unspecified address.");
        } else {

            List<ScanFilter> filters = Arrays.asList( new ScanFilter.Builder().setServiceUuid( ParcelUuid.fromString(UUID_SERVICE) ).build() );

            mScanner = bluetoothAdapter.getBluetoothLeScanner();
            mScanner.startScan(
                filters,
                new ScanSettings.Builder().build(),
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        mScanner.stopScan(this);
                        Log.d(TAG, "onScanResult: " + result);
                        handler.onDeviceDiscovered( new BluetoothDeviceInfo( result.getDevice() ) );
                    }

                    @Override
                    public void onScanFailed(int errorCode) {
                        mScanner.stopScan(this);
                        Log.w(TAG, "Scan Failed. Error code = " + errorCode);
                        handler.onDeviceDiscoveryFailed( errorCode );
                    }

                    @Override
                    public void onBatchScanResults(List<ScanResult> results) {
                        mScanner.stopScan(this);
                        Log.d(TAG, "onBatchScanResults: " + results);
                        Iterator<ScanResult> iter = results.iterator();
                        while ( iter.hasNext() ) {
                            handler.onDeviceDiscovered( new BluetoothDeviceInfo( iter.next().getDevice() ) );
                        }
                    }
                }
            );

            Timer timeoutTimer = new Timer();
            timeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.onDeviceDiscoveryCompleted();
                }
            }, SCAN_TIMEOUT );
        }
    }

    private void addActionToChain(Runnable action) {
        actionChain.add(action);
    }

    private void startActionChain() {
        runNextActionInChain();
    }

    private void runNextActionInChain() {
        if (actionChain.size() > 0) {
            actionChain.pollFirst().run();
        }
    }

    private void clearActionChain() {
        actionChain.clear();
    }

    private boolean sendData(String text) {
        Log.d( TAG, "Sending message \"" + text + "\"" );
        String line = text + "\n";
        byte[] value = line.getBytes(Charset.forName("UTF-8"));
        return sendData(value);
    }

    private boolean sendData(byte[] data) {
        if (mUartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            for (int i = 0; i < data.length; i += TX_MAX_CHARACTERS) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + TX_MAX_CHARACTERS, data.length));
                mBleManager.writeService(mUartService, UUID_TX, chunk);
            }
            return true;
        } else {
            Log.w(TAG, "UART service not available. Unable to send data");
            return false;
        }
    }

    private void readSettings( Activity hostActivity ) {
        Log.d(TAG, "Reading application settings.");
        SharedPreferences settings = hostActivity.getSharedPreferences(TAG, 0);
        mDeviceAddress = settings.getString("deviceAddress", null);
    }

    private void saveSettings( Activity hostActivity ) {
        Log.d(TAG, "Saving BLECommunicator settings.");
        SharedPreferences settings = hostActivity.getSharedPreferences(TAG, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("deviceAddress", mDeviceAddress);
        editor.commit();
    }

    private void keepDialogOnOrientationChanges(Dialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    private BleManager.BleManagerListener createBleManagerListenerImpl() {
        return  new BleManager.BleManagerListener() {
            @Override
            public void onServicesDiscovered() {
                mUartService = mBleManager.getGattService(UUID_SERVICE);
                mBleManager.enableNotification(mUartService, UUID_RX, true);
            }

            @Override
            public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
                if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
                    if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                        byte[] bytes = characteristic.getValue();
                        String message = new String(bytes, Charset.forName("UTF-8"));
                        HPBMData data = messageInterpreter.readMessage(message);
                        if ( dataHandler != null ) {
                            dataHandler.onDataReceived(data);
                        }
                    }
                }
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Disconnected.");
            }

            @Override
            public void onDataAvailable(BluetoothGattDescriptor descriptor) {}

            @Override
            public void onReadRemoteRssi(int rssi) {}

            @Override
            public void onConnected() {}

            @Override
            public void onConnecting() {}
        };
    }

}
