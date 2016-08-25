package hpbm.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hpbm.app.ble.BluetoothDeviceInfo;
import hpbm.app.core.Communicator;
import hpbm.app.core.CommunicatorProvider;
import hpbm.app.core.MessageInterpreterImpl;
import hpbm.app.core.HPBMDevicesDiscoveryHandler;
import hpbm.app.sim.SimCommunicator;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private ListView listView;
    private View devicesView;
    private LinearLayout searchingForDevicesView;
    private List<String> stringValues;
    private Map<String,String> deviceAddressMap;
    private ArrayAdapter<String> adapter;
    private Communicator communicator;
    private String selectedDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        communicator = new SimCommunicator( new MessageInterpreterImpl() );
        //communicator = new BLECommunicator( new MessageInterpreterImpl() );
        CommunicatorProvider.setCommunicator( communicator );

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        devicesView = findViewById(R.id.devicesView);
        listView = (ListView) findViewById(R.id.listView);
        searchingForDevicesView = (LinearLayout) findViewById(R.id.searchingForDevicesView);

        stringValues = new ArrayList<>();
        deviceAddressMap = new HashMap<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, stringValues);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedDeviceName = parent.getItemAtPosition(position).toString();
                Toast.makeText(getApplicationContext(), "Selected " + selectedDeviceName, Toast.LENGTH_LONG).show();
                findViewById(R.id.connectButton).setEnabled( true );
            }
        });

        final Button connectButton = (Button) findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectWithSelectedDevice();
            }
        });

        searchingForDevicesView.setVisibility(View.VISIBLE);
        devicesView.setVisibility(View.GONE);

        communicator.listAvailableDevices(this, new HPBMDevicesDiscoveryHandler() {
            @Override
            public void onDeviceDiscovered(final BluetoothDeviceInfo device) {
                Log.d( TAG, "onDeviceDiscovered: " + device );
                stringValues.add( device.getName() );
                deviceAddressMap.put( device.getName(), device.getAddress() );
            }

            @Override
            public void onDeviceDiscoveryCompleted() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        searchingForDevicesView.setVisibility(View.GONE);
                        devicesView.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onDeviceDiscoveryFailed(int errorCode) {
                // TODO: display error message
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void connectWithSelectedDevice() {
        String deviceAddress = deviceAddressMap.get( selectedDeviceName );
        if ( communicator.connect( this, deviceAddress ) ) {
            Intent in = new Intent(this, DeviceSetupActivity.class);
            startActivity(in);
        } else {
            // TODO: display error message
        }
    }
}
