package hpbm.app.core;

import android.app.Activity;
import android.content.Intent;

public interface Communicator {
    void setDataHandler( HPBMDataHandler dataHandler );
    boolean onRequestPermissionsResult( Activity hostActivity, int requestCode, String permissions[], int[] grantResults );
    boolean onActivityResult( Activity hostActivity, int requestCode, int resultCode, Intent intent );
    void listAvailableDevices( Activity hostActivity, HPBMDevicesDiscoveryHandler handler );
    boolean connect( Activity hostActivity, String hpbmDeviceAddress );
    boolean disconnect( Activity hostActivity );
    boolean sendRefillToMessage( Activity hostActivity, float amount );   // [ml]
    boolean sendRefillWithMessage( Activity hostActivity, float amount ); // [ml]
    boolean sendResetMessage( Activity hostActivity );
}
