package hpbm.app.ble;

import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceInfo {

    private final String address;
    private final String name;

    public BluetoothDeviceInfo(String address, String name) {
        this.address = address;
        this.name = name;
    }

    public BluetoothDeviceInfo(BluetoothDevice device) {
        this.address = device.getAddress();
        this.name = device.getName();
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Bluetooth device: " + name + " at " + address;
    }
}
