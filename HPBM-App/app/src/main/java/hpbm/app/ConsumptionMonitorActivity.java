package hpbm.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

import hpbm.app.core.Communicator;
import hpbm.app.core.CommunicatorProvider;
import hpbm.app.core.HPBMData;
import hpbm.app.core.HPBMDataHandler;

public class ConsumptionMonitorActivity extends AppCompatActivity {

    private static final String TAG = ConsumptionMonitorActivity.class.getSimpleName();

    private Communicator communicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consumption_monitor);

        communicator = CommunicatorProvider.getCommunicator();
        communicator.setDataHandler(new HPBMDataHandler() {
            @Override
            public void onDataReceived(HPBMData data) {
                displayHPBMData( data );
            }
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle("Consumption Monitor");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView fp = (TextView) findViewById(R.id.fill_percentage);
        fp.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "Comfortaa-Bold.ttf"));

        setGaugeValue(1f);
        setCurrentConsumptionValue(0f);
        setAverageConsumptionValue(0f);
        setTimeToEmpty(0);

    }

    @Override
    protected void onResume() {
        Log.d( TAG, "Resuming..." );
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        communicator.onRequestPermissionsResult( this, requestCode, permissions, grantResults );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        communicator.onActivityResult( this, requestCode, resultCode, intent );
    }

    private void displayHPBMData(final HPBMData data ) {
        if ( data == null ) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setGaugeValue( data.getRemainingPart() );
                setCurrentConsumptionValue( data.getCurrentConsumption() );
                setAverageConsumptionValue( data.getAverageConsumption()  );
                setTimeToEmpty( data.getTimeToEmpty() );
            }
        });
    }

    private void setGaugeValue( float gaugeValue ) {
        GaugeView gauge = (GaugeView) findViewById( R.id.gauge );
        gauge.setValue( gaugeValue );
        TextView fp = (TextView) findViewById(R.id.fill_percentage);
        fp.setText( String.format( "%.0f%%", 100f*gaugeValue ) );
    }

    private void setCurrentConsumptionValue( float currentConsumptionValue ) {
        MonitorValueDisplay currentConsumptionField = (MonitorValueDisplay) findViewById(R.id.current_consumption);
        currentConsumptionField.setValue( currentConsumptionValue );

    }

    private void setAverageConsumptionValue( float averageConsumptionValue ) {
        MonitorValueDisplay averageConsumptionField = (MonitorValueDisplay) findViewById(R.id.average_consumption);
        averageConsumptionField.setValue( averageConsumptionValue );
    }

    private void setTimeToEmpty(float timeToEmpty ) {
        MonitorValueDisplay timeToEmptyField = (MonitorValueDisplay) findViewById(R.id.time_till_empty);
        int hours = (int) Math.floor(timeToEmpty/3600f);
        int minutes = (int) Math.floor( (timeToEmpty-hours*3600f)/60f );
        timeToEmptyField.setValue(hours, minutes);
    }

}
