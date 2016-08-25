package hpbm.app.core;

import android.util.Log;

import java.util.StringTokenizer;

public class MessageInterpreterImpl implements MessageInterpreter {

    private static final String TAG = MessageInterpreterImpl.class.getSimpleName();

    @Override
    public HPBMData readMessage(String message) {
        // Message pattern: <current consumption [ml/s]>,<average consumption [ml/s]>,<amount remaining [ml]>,<time to empty [s]>
        StringTokenizer tokenizer = new StringTokenizer( message, "," );
        if ( tokenizer.countTokens() == 4 ) {
            try {
                float currentConsumption = Float.parseFloat(tokenizer.nextToken());
                float averageConsumption = Float.parseFloat(tokenizer.nextToken());
                float remainingPart = Float.parseFloat(tokenizer.nextToken());
                int timeToEmpty = Integer.parseInt(tokenizer.nextToken());
                return new HPBMData(currentConsumption, averageConsumption, remainingPart, timeToEmpty);
            } catch ( NumberFormatException ex ) {
                Log.w( TAG, ex );
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public String createRefillToMessage(float newTotalAmount) {
        return "RT:" + new Float(newTotalAmount).intValue();
    }

    @Override
    public String createRefillWithMessage(float refillAmount) {
        return "RW:" + new Float(refillAmount).intValue();
    }

    @Override
    public String createResetMessage() {
        return "RST";
    }

}
