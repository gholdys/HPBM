// Adapted from: https://github.com/StevenRudenko/BleSensorTag. MIT License (Steven Rudenko)

package hpbm.app.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

class BleManager {

    private final static String TAG = BleManager.class.getSimpleName();
    private static final String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static final int STATE_DISCONNECTED = 0;
    static final int STATE_CONNECTING = 1;
    static final int STATE_CONNECTED = 2;

    public interface BleManagerListener {

        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onServicesDiscovered();

        void onDataAvailable(BluetoothGattCharacteristic characteristic);

        void onDataAvailable(BluetoothGattDescriptor descriptor);

        void onReadRemoteRssi(int rssi);
    }

    private interface ServiceAction {
        /**
         * Executes action.
         *
         * @param bluetoothGatt
         * @return true - if action was executed instantly. false if action is waiting for feedback.
         */
        boolean execute(BluetoothGatt bluetoothGatt);
    }

    private final BluetoothGattCallback mGattCallback = createGattCallbackImpl();
    private final BluetoothAdapter mAdapter;
    private BluetoothGatt mGatt;
    private final Context mContext;
    private BluetoothDevice mDevice;
    private String mDeviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

    private final BleManagerListener mBleListener;
    private final LinkedList<ServiceAction> mQueue = new LinkedList<>();
    private volatile ServiceAction mCurrentAction;


    BleManager(Context context, BleManagerListener listener) {
        // Init Adapter
        mContext = context.getApplicationContext();
        mBleListener = listener;
        mAdapter = BleUtils.getBluetoothAdapter(mContext);

        if (mAdapter == null || !mAdapter.isEnabled()) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }
    }

    int getState() {
        return mConnectionState;
    }

    BluetoothDevice getConnectedDevice() {
        return mDevice;
    }

    String getConnectedDeviceAddress() {
        return mDeviceAddress;
    }

    boolean isAdapterEnabled() {
        return mAdapter != null && mAdapter.isEnabled();
    }

    BluetoothAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
     */
    boolean connect(Context context, String address) {
        if (mAdapter == null || !mAdapter.isEnabled() || address == null) {
            Log.w(TAG, "connect: BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Get preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean reuseExistingConnection = sharedPreferences.getBoolean("pref_recycleconnection", false);

        if (reuseExistingConnection) {
            // Previously connected device.  Try to reconnect.
            if (mDeviceAddress != null && address.equalsIgnoreCase(mDeviceAddress) && mGatt != null) {
                Log.d(TAG, "Trying to use an existing BluetoothGatt for connection.");
                if (mGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    if (mBleListener != null)
                        mBleListener.onConnecting();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            final boolean forceCloseBeforeNewConnection = sharedPreferences.getBoolean("pref_forcecloseconnection", true);

            if (forceCloseBeforeNewConnection) {
                close();
            }
        }

        mDevice = mAdapter.getRemoteDevice(address);
        if (mDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        Log.d(TAG, "Device at \"" + address + "\" found. Connecting...");

        mDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        if (mBleListener != null) {
            mBleListener.onConnecting();
        }

        final boolean gattAutoconnect = sharedPreferences.getBoolean("pref_gattautoconnect", false);
        mGatt = mDevice.connectGatt(mContext, gattAutoconnect, mGattCallback);

        return true;
    }

    /**
     * Call to private Android method 'refresh'
     * This method does actually clear the cache from a bluetooth device. But the problem is that we don't have access to it. But in java we have reflection, so we can access this method.
     * http://stackoverflow.com/questions/22596951/how-to-programmatically-force-bluetooth-low-energy-service-discovery-on-android
     */
    boolean refreshDeviceCache() {
        try {
            BluetoothGatt localBluetoothGatt = mGatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean result = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                if (result) {
                    Log.d(TAG, "Bluetooth refresh cache");
                }
                return result;
            }
        } catch (Exception localException) {
            Log.e(TAG, "An exception occurred while refreshing device");
        }
        return false;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
     */
    void disconnect() {
        mDevice = null;
        if (mAdapter == null || mGatt == null) {
            Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
            return;
        }
        mGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are  released properly.
     */
    void close() {
        mQueue.clear();
        mCurrentAction = null;
        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
            mDeviceAddress = null;
            mDevice = null;
        }
    }

    boolean readRssi() {
        if (mGatt != null) {
            return mGatt.readRemoteRssi();  // if true: Caller should wait for onReadRssi callback
        } else {
            return false;           // Rsii read is not available
        }
    }

    void readCharacteristic(BluetoothGattService service, String characteristicUUID) {
        readService(service, characteristicUUID, null);
    }

    void readDescriptor(BluetoothGattService service, String characteristicUUID, String descriptorUUID) {
        readService(service, characteristicUUID, descriptorUUID);
    }

    void writeService(BluetoothGattService service, String uuid, byte[] value) {
        if (service != null) {
            if (mAdapter == null || mGatt == null) {
                Log.w(TAG, "writeService: BluetoothAdapter not initialized");
                return;
            }

            addActionToQueue( createServiceWriteAction(service, uuid, value) );
            executeNextActionFromQueue(mGatt);
        }
    }

    void enableNotification(BluetoothGattService service, String uuid, boolean enabled) {
        if (service != null) {

            if (mAdapter == null || mGatt == null) {
                Log.w(TAG, "enableNotification: BluetoothAdapter not initialized");
                return;
            }

            addActionToQueue( createServiceNotifyAction(service, uuid, enabled) );
            executeNextActionFromQueue(mGatt);
        }
    }

    void enableIndication(BluetoothGattService service, String uuid, boolean enabled) {
        if (service != null) {

            if (mAdapter == null || mGatt == null) {
                Log.w(TAG, "enableNotification: BluetoothAdapter not initialized");
                return;
            }

            addActionToQueue( createServiceIndicateAction(service, uuid, enabled) );
            executeNextActionFromQueue(mGatt);
        }
    }

    int getCharacteristicProperties(BluetoothGattService service, String characteristicUUIDString) {
        final UUID characteristicUuid = UUID.fromString(characteristicUUIDString);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        int properties = 0;
        if (characteristic != null) {
            properties = characteristic.getProperties();
        }

        return properties;
    }

    boolean isCharacteristicReadable(BluetoothGattService service, String characteristicUUIDString) {
        final int properties = getCharacteristicProperties(service, characteristicUUIDString);
        final boolean isReadable = (properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
        return isReadable;
    }

    boolean isCharacteristicNotifiable(BluetoothGattService service, String characteristicUUIDString) {
        final int properties = getCharacteristicProperties(service, characteristicUUIDString);
        final boolean isNotifiable = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        return isNotifiable;
    }

    int getDescriptorPermissions(BluetoothGattService service, String characteristicUUIDString, String descriptorUUIDString) {
        final UUID characteristicUuid = UUID.fromString(characteristicUUIDString);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);

        int permissions = 0;
        if (characteristic != null) {
            final UUID descriptorUuid = UUID.fromString(descriptorUUIDString);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
            if (descriptor != null) {
                permissions = descriptor.getPermissions();
            }
        }

        return permissions;
    }

    boolean isDescriptorReadable(BluetoothGattService service, String characteristicUUIDString, String descriptorUUIDString) {
        final int permissions = getDescriptorPermissions(service, characteristicUUIDString, descriptorUUIDString);
        final boolean isReadable = (permissions & BluetoothGattCharacteristic.PERMISSION_READ) != 0;
        return isReadable;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    List<BluetoothGattService> getSupportedGattServices() {
        if (mGatt != null) {
            return mGatt.getServices();
        } else {
            return null;
        }
    }

    BluetoothGattService getGattService(String uuid) {
        if (mGatt != null) {
            final UUID serviceUuid = UUID.fromString(uuid);
            return mGatt.getService(serviceUuid);
        } else {
            return null;
        }
    }

    BluetoothGattService getGattService(String uuid, int instanceId) {
        if (mGatt != null) {
            List<BluetoothGattService> services = getSupportedGattServices();
            boolean found = false;
            int i = 0;
            while (i < services.size() && !found) {
                BluetoothGattService service = services.get(i);
                if (service.getUuid().toString().equalsIgnoreCase(uuid) && service.getInstanceId() == instanceId) {
                    found = true;
                } else {
                    i++;
                }
            }

            if (found) {
                return services.get(i);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void readService(BluetoothGattService service, String characteristicUUID, String descriptorUUID) {
        if (service != null) {
            if (mAdapter == null || mGatt == null) {
                Log.w(TAG, "readService: BluetoothAdapter not initialized");
                return;
            }

            addActionToQueue( createServiceReadAction(service, characteristicUUID, descriptorUUID) );
            executeNextActionFromQueue(mGatt);
        }
    }

    private ServiceAction createServiceReadAction(final BluetoothGattService gattService, final String characteristicUuidString, final String descriptorUuidString) {
        return new ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
                if (characteristic != null) {
                    if (descriptorUuidString == null) {
                        // Read Characteristic
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            bluetoothGatt.readCharacteristic(characteristic);
                            return false;
                        } else {
                            Log.w(TAG, "Read: characteristic not readable: " + characteristicUuidString);
                            return true;
                        }
                    } else {
                        // Read Descriptor
                        final UUID descriptorUuid = UUID.fromString(descriptorUuidString);
                        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptorUuid);
                        if (descriptor != null) {
                            bluetoothGatt.readDescriptor(descriptor);
                            return false;
                        } else {
                            Log.w(TAG, "Read: descriptor not found: " + descriptorUuidString);
                            return true;
                        }
                    }
                } else {
                    Log.w(TAG, "Read: characteristic not found: " + characteristicUuidString);
                    return true;
                }
            }
        };
    }

    private ServiceAction createServiceNotifyAction(final BluetoothGattService gattService, final String characteristicUuidString, final boolean enable) {
        return new ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                if (characteristicUuidString != null) {
                    final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
                    final BluetoothGattCharacteristic dataCharacteristic = gattService.getCharacteristic(characteristicUuid);

                    if (dataCharacteristic == null) {
                        Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + " not found");
                        return true;
                    }

                    final UUID clientCharacteristicConfiguration = UUID.fromString(CHARACTERISTIC_CONFIG);
                    final BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(clientCharacteristicConfiguration);
                    if (config == null)
                        return true;

                    // enableNotification/disable locally
                    bluetoothGatt.setCharacteristicNotification(dataCharacteristic, enable);
                    // enableNotification/disable remotely
                    config.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(config);

                    return false;
                } else {
                    Log.w(TAG, "Characteristic UUID is null");
                    return true;
                }
            }
        };
    }

    private ServiceAction createServiceIndicateAction(final BluetoothGattService gattService, final String characteristicUuidString, final boolean enable) {
        return new ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                if (characteristicUuidString != null) {
                    final UUID characteristicUuid = UUID.fromString(characteristicUuidString);
                    final BluetoothGattCharacteristic dataCharacteristic = gattService.getCharacteristic(characteristicUuid);

                    if (dataCharacteristic == null) {
                        Log.w(TAG, "Characteristic with UUID " + characteristicUuidString + " not found");
                        return true;
                    }

                    final UUID clientCharacteristicConfiguration = UUID.fromString(CHARACTERISTIC_CONFIG);
                    final BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(clientCharacteristicConfiguration);
                    if (config == null)
                        return true;

                    // enableNotification/disable remotely
                    config.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(config);

                    return false;
                } else {
                    Log.w(TAG, "Characteristic UUID is null");
                    return true;
                }
            }
        };
    }

    private ServiceAction createServiceWriteAction(final BluetoothGattService gattService, final String uuid, final byte[] value) {
        return new ServiceAction() {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                final UUID characteristicUuid = UUID.fromString(uuid);
                final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(characteristicUuid);
                if (characteristic != null) {
                    characteristic.setValue(value);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    return false;
                } else {
                    Log.w(TAG, "Write: characteristic not found: " + uuid);
                    return true;
                }
            }
        };
    }

    private void addActionToQueue( ServiceAction action ) {
        mQueue.add(action);
    }


    private void executeNextActionFromQueue(BluetoothGatt gatt) {
        if (mCurrentAction == null) {
            while (!mQueue.isEmpty()) {
                ServiceAction action = mQueue.pop();
                mCurrentAction = action;
                if (!action.execute(gatt))
                    break;
                mCurrentAction = null;
            }
        }
    }

    private BluetoothGattCallback createGattCallbackImpl() {
        return new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mConnectionState = STATE_CONNECTED;

                    if (mBleListener != null) {
                        mBleListener.onConnected();
                    }

                    // Attempts to discover services after successful connection.
                    gatt.discoverServices();

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mConnectionState = STATE_DISCONNECTED;
                    mQueue.clear();
                    mCurrentAction = null;

                    if (mBleListener != null) {
                        mBleListener.onDisconnected();
                    }

                } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                    mConnectionState = STATE_CONNECTING;

                    if (mBleListener != null) {
                        mBleListener.onConnecting();
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d(TAG, "onServicesDiscovered status: " + status);
                if (mBleListener != null) {
                    mBleListener.onServicesDiscovered();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                mCurrentAction = null;
                executeNextActionFromQueue(gatt);

                if (mBleListener != null) {
                    mBleListener.onDataAvailable(characteristic);
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onCharacteristicRead status: " + status);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (mBleListener != null) {
                    mBleListener.onDataAvailable(characteristic);
                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                mCurrentAction = null;
                executeNextActionFromQueue(gatt);

                if (mBleListener != null) {
                    mBleListener.onDataAvailable(descriptor);
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onDescriptorRead status: " + status);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                mCurrentAction = null;
                executeNextActionFromQueue(gatt);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                mCurrentAction = null;
                executeNextActionFromQueue(gatt);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                if (mBleListener != null) {
                    mBleListener.onReadRemoteRssi(rssi);
                }

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "onReadRemoteRssi status: " + status);
                }
            }
        };
    }

}
