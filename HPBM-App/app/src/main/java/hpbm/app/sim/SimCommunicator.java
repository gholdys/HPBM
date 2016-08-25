package hpbm.app.sim;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import hpbm.app.ble.BluetoothDeviceInfo;
import hpbm.app.core.Communicator;
import hpbm.app.core.HPBMDataHandler;
import hpbm.app.core.MessageInterpreter;
import hpbm.app.core.HPBMData;
import hpbm.app.core.HPBMDevicesDiscoveryHandler;

public class SimCommunicator implements Communicator {


    private static final String TAG = SimCommunicator.class.getSimpleName();

    private float refillAmount = 1000f;
    private SimDataGenerator dataGenerator;
    private Timer dataReadingTimer;
    private HPBMDataHandler dataHandler;

    public SimCommunicator(MessageInterpreter messageInterpreter ) {
        this.dataGenerator = new SimDataGenerator();
    }

    @Override
    public void setDataHandler(HPBMDataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    @Override
    public boolean onRequestPermissionsResult(Activity hostActivity, int requestCode, String[] permissions, int[] grantResults) {
        return false;
    }

    @Override
    public boolean onActivityResult(Activity hostActivity, int requestCode, int resultCode, Intent intent) {
        return false;
    }

    @Override
    public void listAvailableDevices( Activity hostActivity, final HPBMDevicesDiscoveryHandler handler ) {
        Timer scanTimer = new Timer();
        scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.onDeviceDiscovered( new BluetoothDeviceInfo("00:11:22:33", "HPBM-Device #1"));
            }
        }, 1000);
        scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.onDeviceDiscovered( new BluetoothDeviceInfo("44:55:66:77", "HPBM-Device #2"));
                handler.onDeviceDiscoveryCompleted();
            }
        }, 2000);
    }

    @Override
    public boolean connect( Activity hostActivity, String hpbmDeviceAddress ) {
        Log.d(TAG, "Starting simulation...");
        dataGenerator.refill( refillAmount );
        dataReadingTimer = new Timer();
        dataReadingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                HPBMData data = dataGenerator.getData();
                if ( dataHandler != null ) {
                    dataHandler.onDataReceived(data);
                }
            }
        }, 1000, 1000);
        return true;
    }

    @Override
    public boolean disconnect( Activity hostActivity  ) {
        dataReadingTimer.cancel();
        dataReadingTimer = null;
        return true;
    }

    @Override
    public boolean sendRefillToMessage(Activity hostActivity, float amount) {
        refillAmount = amount;
        dataGenerator.refill( refillAmount );
        return true;
    }

    @Override
    public boolean sendRefillWithMessage(Activity hostActivity, float amount) {
        refillAmount = dataGenerator.getCurrentWaterAmount()+amount;
        dataGenerator.refill( refillAmount );
        return true;
    }

    @Override
    public boolean sendResetMessage(Activity hostActivity) {
        return true;
    }

}
