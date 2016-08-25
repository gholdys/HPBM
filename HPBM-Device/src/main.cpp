#include "Arduino.h"
#include "Timer.h"
#include <SPI.h>
#include <Adafruit_BLE.h>
#include <Adafruit_BluefruitLE_SPI.h>
#include <Adafruit_BluefruitLE_UART.h>
#include "BluefruitConfig.h"

void error(const __FlashStringHelper*err);
void refillTo( float waterAmount );
void processData();
void takeReading();
float calculateAverageConsumptionRate();
void sendToBLE( float ccr, float acr, float rp, int tte );
void sendToStream( Stream& s, float ccr, float acr, float rp, int tte );
char* readFromBLE();
void parseUserCommand( char* command );
void stopTimer();
void startTimer();

#define FACTORYRESET_ENABLE         1
#define MINIMUM_FIRMWARE_VERSION    "0.6.6"
#define MODE_LED_BEHAVIOUR          "MODE"
#define SENSOR_PIN                  11
#define SAMPLE_MILLIS               1000
#define INITIAL_WATER_AMOUNT        200         // [ml]
#define PULSE_TO_CONSUMED_COEFF     0.135       // [ml]  Original value: 0.2281
#define LED_PIN                     12

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

Timer timer;
int timerAction;
float initialWaterAmount;                   // [ml]
float currentWaterAmount;                   // [ml]
long startTime;                             // [ms]
float consumed;                             // [ml]
float averageConsumptionRate;               // [ml/s]
float remainingPart;                        // []
int timeToEmpty;                            // [s]
float currentConsumptionRate;               // [ml]
volatile int pulseCounter;

void setup() {
    Serial.begin(115200);
    Serial.println(F("HPBM-Device"));
    Serial.println(F("---------------------------------------"));

    pinMode( LED_PIN, OUTPUT );
    PCICR  = RISING;        // enable pin-change interrupts
    PCMSK0 = bit(PCINT7);   // enable PCINT7 pin
    DDRB  &= 0xff;          // configure port B pins for input
    PORTB |= 0xff;          // enable pull-up resistors on all port B pins

    refillTo( INITIAL_WATER_AMOUNT );
    startTimer();

    /* Initialise the module */
    Serial.print(F("Initialising the Bluefruit LE module: "));

    if ( !ble.begin(VERBOSE_MODE) ) {
        error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring."));
    }
    Serial.println( F("OK!") );

    if ( FACTORYRESET_ENABLE ) {
        /* Perform a factory reset to make sure everything is in a known state */
        Serial.println(F("Performing a factory reset: "));
        if ( ! ble.factoryReset() ) {
            error(F("Couldn't do a factory reset"));
        }
    }

    /* Disable command echo from Bluefruit */
    ble.echo(false);

    Serial.println(F("Setting device name to 'HPBM-Device #1': "));
    if (! ble.sendCommandCheckOK(F( "AT+GAPDEVNAME=HPBM-Device #1" )) ) {
        error(F("Could not set device name!"));
    }

    /* Reset the device for the changes to take effect */
    Serial.println(F("Performing a SW reset: "));
    if (! ble.reset() ) {
        error(F("Couldn't reset??"));
    }

    Serial.print(F("Initial water amount: "));
    Serial.print( currentWaterAmount );
    Serial.println(F(" ml"));

    pulseCounter = 0;
    sei();
}

void loop() {
    timer.update();
}

ISR( PCINT0_vect ) {
    pulseCounter++;
}

void refillTo( float waterAmount ) {
    Serial.print(F("Refilling to "));
    Serial.print(waterAmount);
    Serial.println(" ml");
    initialWaterAmount = waterAmount;
    currentWaterAmount = initialWaterAmount;
    startTime = millis();
}

void stopTimer() {
    timer.stop( timerAction );
}

void startTimer() {
    timerAction = timer.every(SAMPLE_MILLIS, processData);        
}

void processData() {
    takeReading();
    sendToBLE( currentConsumptionRate, averageConsumptionRate, remainingPart, timeToEmpty );
    char* userCommand = readFromBLE();
    if ( userCommand != NULL ) {
        parseUserCommand( userCommand );
    }
}

void takeReading() {
    // Original formula: consumed rate [Litres/hour] = (pulses per second x 60) / 73
    consumed = PULSE_TO_CONSUMED_COEFF * pulseCounter; // [ml]
    pulseCounter = 0;
    currentWaterAmount -= consumed; // [ml]

    if (currentWaterAmount <= 0.0) {
        currentConsumptionRate = 0.0;
        remainingPart = 0.0;
        timeToEmpty = 0;
    } else {
        currentConsumptionRate = consumed*1000/SAMPLE_MILLIS;  // [ml/s]
        remainingPart = currentWaterAmount / initialWaterAmount;
        if (startTime == -1) {  // First measurement
            averageConsumptionRate = -1;
            timeToEmpty = -1;
        } else {
            averageConsumptionRate = calculateAverageConsumptionRate();  // [ml/s]
            timeToEmpty = (int) (currentWaterAmount / averageConsumptionRate);  // [s]
        }
    }
}

float calculateAverageConsumptionRate() {  // [ml/s]
    int dt = (millis() - startTime)/1000;
    return (initialWaterAmount - currentWaterAmount)/dt;
}

void sendToBLE( float ccr, float acr, float rp, int tte ) {
    digitalWrite(LED_PIN, HIGH);
    ble.print("AT+BLEUARTTX=");
    sendToStream(ble, ccr, acr, rp, tte);
    ble.waitForOK();
    digitalWrite(LED_PIN, LOW);
}

void parseUserCommand( char* userCommand ) {
    if ( strncmp(userCommand, "RT:", 3 ) == 0 ) {
        float refillAmount = String( userCommand+3 ).toFloat();
        stopTimer();
        refillTo( refillAmount );
        startTimer();
    }
}

void sendToStream( Stream& s, float ccr, float acr, float rp, int tte ) {
    s.print(ccr);
    s.print(",");
    s.print(acr);
    s.print(",");
    s.print(rp);
    s.print(",");
    s.println(tte);
}

char* readFromBLE() {
    // Check for incoming characters from Bluefruit
    ble.println("AT+BLEUARTRX");
    ble.readline();
    if (strcmp(ble.buffer, "OK") == 0) {
        // no data
        return NULL;
    }
    // Some data was found. It's in the buffer:
    Serial.print(F("Recieved: "));
    Serial.println(ble.buffer);
    ble.waitForOK();
    return ble.buffer;
}

void error(const __FlashStringHelper*err) {
    Serial.println(err);
    while (1);
}
