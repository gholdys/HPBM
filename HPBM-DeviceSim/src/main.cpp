#include "Arduino.h"
#include "Timer.h"

#include <SPI.h>
#include <Adafruit_BLE.h>
#include <Adafruit_BluefruitLE_SPI.h>
#include <Adafruit_BluefruitLE_UART.h>

#include "BluefruitConfig.h"

void error(const __FlashStringHelper*err);
void takeReading();
float calculateAverageConsumptionRate();
void simulateConsumption();
void consume( float amount );

#define FACTORYRESET_ENABLE         1
#define MINIMUM_FIRMWARE_VERSION    "0.6.6"
#define MODE_LED_BEHAVIOUR          "MODE"

Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

Timer timer;
float initialWaterAmount;                       // [ml]
float currentWaterAmount;                       // [ml]
long startTime;                                 // [ms]
volatile float currentConsumptionRate;          // [ml] volatile because of access from the timer
volatile float consumedSinceLastMeasurement;    // [ml] volatile because of access from the timer


void setup() {
    while (!Serial);  // required for Flora & Micro
    delay(500);

    Serial.begin(115200);
    Serial.println(F("HPBM Simulator"));
    Serial.println(F("---------------------------------------"));

    initialWaterAmount = 1000.0;
    currentWaterAmount = initialWaterAmount;
    timer.every(1000, takeReading);
    timer.every(512, simulateConsumption);

    /* Initialise the module */
    Serial.print(F("Initialising the Bluefruit LE module: "));

    if ( !ble.begin(VERBOSE_MODE) ) {
        error(F("Couldn't find Bluefruit, make sure it's in CoMmanD mode & check wiring?"));
    }
    Serial.println( F("OK!") );

    if ( FACTORYRESET_ENABLE ) {
        /* Perform a factory reset to make sure everything is in a known state */
        Serial.println(F("Performing a factory reset: "));
        if ( ! ble.factoryReset() ) {
            error(F("Couldn't factory reset"));
        }
    }

    /* Disable command echo from Bluefruit */
    ble.echo(false);

    /* Wait for connection */
    // while (! ble.isConnected()) {
    //     delay(500);
    // }

    Serial.println(F("Ready"));
}

void loop() {
    timer.update();
}

void takeReading() {
    currentConsumptionRate = consumedSinceLastMeasurement;
    consumedSinceLastMeasurement = 0;

    float averageConsumptionRate = calculateAverageConsumptionRate();  // [ml/s]
    float remainingPart = currentWaterAmount / initialWaterAmount;
    int timeToEmpty = (int) (currentWaterAmount / averageConsumptionRate);  // [s]

    ble.print("AT+BLEUARTTX=");
    // ble.print("D:");
    ble.print(currentConsumptionRate);
    ble.print(",");
    ble.print(averageConsumptionRate);
    ble.print(",");
    ble.print(remainingPart);
    ble.print(",");
    ble.println(timeToEmpty);
}

void simulateConsumption() {
    consume(2.0);
}

float calculateAverageConsumptionRate() {  // [ml/s]
    int dt = (millis() - startTime)/1000;
    return (initialWaterAmount - currentWaterAmount)/dt;
}

void consume( float amount ) {
    if ( amount <= 0.0 ) return;
    if ( currentWaterAmount <= 0.0 ) return;

    currentWaterAmount -= amount;
    consumedSinceLastMeasurement += amount;
    if (startTime == -1) {
        startTime = millis();
    } else if (currentWaterAmount <= 0.0) {
        currentConsumptionRate = 0.0;
        consumedSinceLastMeasurement = 0.0;
        startTime = -1;
    }

}

void error(const __FlashStringHelper*err) {
  Serial.println(err);
  while (1);
}
