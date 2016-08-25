package hpbm.app.core;

public interface MessageInterpreter {
    HPBMData readMessage(String message );
    String createRefillToMessage( float newTotalAmount );  // [ml]
    String createRefillWithMessage( float refillAmount );  // [ml]
    String createResetMessage();
}
