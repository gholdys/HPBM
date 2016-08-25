package hpbm.app.core;

import hpbm.app.ble.BluetoothDeviceInfo;

public interface HPBMDevicesDiscoveryHandler {
    void onDeviceDiscovered( BluetoothDeviceInfo device );
    void onDeviceDiscoveryCompleted();
    void onDeviceDiscoveryFailed( int errorCode );
}
