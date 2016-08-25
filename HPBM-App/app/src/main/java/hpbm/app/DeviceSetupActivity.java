package hpbm.app;

import android.content.Intent;
import android.net.ParseException;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import hpbm.app.core.CommunicatorProvider;

public class DeviceSetupActivity extends AppCompatActivity {

    private int initWaterAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_setup);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if(toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle("Device Setup");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final Button startButton = (Button) findViewById(R.id.button_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });

        final EditText initialWaterVolumeEditor = (EditText) findViewById(R.id.editor_initial_water_volume);
        initialWaterVolumeEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    initWaterAmount = Integer.parseInt(s.toString());
                    startButton.setEnabled(s.length() > 0);
                } catch (ParseException ex) {
                    Log.w( getClass().getSimpleName(), ex );
                    // Ignore
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

    }

    private void start() {
        CommunicatorProvider.getCommunicator().sendRefillToMessage( this, initWaterAmount );
        Intent in = new Intent(DeviceSetupActivity.this, ConsumptionMonitorActivity.class);
        startActivity(in);
    }
}
