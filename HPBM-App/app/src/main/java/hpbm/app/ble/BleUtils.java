package hpbm.app.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public final class BleUtils {

    private final static String TAG = BleUtils.class.getSimpleName();

    public static final int STATUS_BLE_ENABLED = 0;
    public static final int STATUS_BLUETOOTH_NOT_AVAILABLE = 1;
    public static final int STATUS_BLE_NOT_AVAILABLE = 2;
    public static final int STATUS_BLUETOOTH_DISABLED = 3;

    private static ResetBluetoothAdapter sResetHelper;

    // Use this check to determine whether BLE is supported on the device.  Then you can  selectively disable BLE-related features.
    public static int getBleStatus(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return STATUS_BLE_NOT_AVAILABLE;
        }

        final BluetoothAdapter adapter = getBluetoothAdapter(context);
        // Checks if Bluetooth is supported on the device.
        if (adapter == null) {
            return STATUS_BLUETOOTH_NOT_AVAILABLE;
        }

        if (!adapter.isEnabled()) {
            return STATUS_BLUETOOTH_DISABLED;
        }

        return STATUS_BLE_ENABLED;
    }

    // Initializes a Bluetooth adapter.
    public static BluetoothAdapter getBluetoothAdapter(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return null;
        } else {
            return bluetoothManager.getAdapter();
        }
    }

    public static void resetBluetoothAdapter(Context context, ResetBluetoothAdapterListener listener) {
        if (sResetHelper == null) {
            sResetHelper = new ResetBluetoothAdapter(context, listener);
        } else {
            Log.w(TAG, "Reset already in progress");
        }
    }

    public static void cancelBluetoothAdapterReset() {
        if (isBluetoothAdapterResetInProgress()) {
            sResetHelper.cancel();
            sResetHelper = null;
        }
    }

    public static boolean isBluetoothAdapterResetInProgress() {
        return sResetHelper != null;
    }

    private static class ResetBluetoothAdapter {
        private Context mContext;
        private ResetBluetoothAdapterListener mListener;

        private final BroadcastReceiver mBleAdapterStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    onBleAdapterStatusChanged(state);
                }
            }
        };

        ResetBluetoothAdapter(Context context, ResetBluetoothAdapterListener listener) {
            mContext = context;
            mListener = listener;

            // Set receiver
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(mBleAdapterStateReceiver, filter);

            // Reset
            BluetoothAdapter bleAdapter = BleUtils.getBluetoothAdapter(mContext);
            if (bleAdapter.isEnabled()) {
                boolean isDisablingBle = bleAdapter.disable();
                if (isDisablingBle) {
                    Log.w(TAG, "Reset ble adapter started. Waiting to turn off");
                    // Now wait for BluetoothAdapter.ACTION_STATE_CHANGED notification
                } else {
                    Log.w(TAG, "Can't disable bluetooth adapter");
                    resetCompleted();
                }
            }
        }

        private void onBleAdapterStatusChanged(int state) {
            switch (state) {
                case BluetoothAdapter.STATE_OFF: {
                    // Turn off has finished. Turn it on again
                    Log.d(TAG, "Ble adapter turned off. Turning on");
                    BluetoothAdapter bleAdapter = BleUtils.getBluetoothAdapter(mContext);
                    bleAdapter.enable();
                    break;
                }
                case BluetoothAdapter.STATE_TURNING_OFF:
                    break;
                case BluetoothAdapter.STATE_ON: {
                    Log.d(TAG, "Ble adapter turned on. Reset completed");
                    // Turn on has finished.
                    resetCompleted();
                    break;
                }
                case BluetoothAdapter.STATE_TURNING_ON:
                    break;
            }
        }

        private void resetCompleted() {
            mContext.unregisterReceiver(mBleAdapterStateReceiver);
            if (mListener != null) {
                mListener.resetBluetoothCompleted();
            }
            sResetHelper = null;
        }

        public void cancel() {
            try {
                mContext.unregisterReceiver(mBleAdapterStateReceiver);
            } catch (IllegalArgumentException ignored) {}
        }

    }

    public static UUID getUuidFromByteArrayBigEndian(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        UUID uuid = new UUID(high, low);
        return uuid;
    }

    public static UUID getUuidFromByteArraLittleEndian(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long high = bb.getLong();
        long low = bb.getLong();
        UUID uuid = new UUID(low, high);
        return uuid;
    }

    interface ResetBluetoothAdapterListener {
        void resetBluetoothCompleted();
    }
}
