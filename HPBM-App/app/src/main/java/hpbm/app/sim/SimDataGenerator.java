package hpbm.app.sim;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import hpbm.app.core.HPBMData;

class SimDataGenerator {

    private final Timer measurementTimer;
    private final Timer consumptionTimer;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private float initialWaterAmount;                       // [ml]
    private float currentWaterAmount;                       // [ml]
    private long startTime;                                 // [ms]
    private volatile float currentConsumptionRate;          // [ml] volatile because of access from the timer
    private volatile float consumedSinceLastMeasurement;    // [ml] volatile because of access from the timer


    SimDataGenerator() {
        measurementTimer = new Timer();
        consumptionTimer = new Timer();
    }

    boolean isEmpty() {
        lock.readLock().lock();
        try {
            return currentWaterAmount == 0f;
        } finally {
            lock.readLock().unlock();
        }
    }

    void refill( float totalAmount ) {
        lock.writeLock().lock();
        try {
            this.initialWaterAmount = totalAmount;
            this.currentWaterAmount = totalAmount;
            this.startTime = -1;
            consumptionTimer.schedule( createSimulatedConsumptionTask(), 1000);
        } finally {
            lock.writeLock().unlock();
        }
    }

    float getInitialWaterAmount() {
        return initialWaterAmount;
    }

    float getCurrentWaterAmount() {
        lock.readLock().lock();
        try {
            return currentWaterAmount;
        } finally {
            lock.readLock().unlock();
        }
    }

    float getCurrentConsumptionRate() {  // [ml/s]
        lock.readLock().lock();
        try {
            return currentConsumptionRate;
        } finally {
            lock.readLock().unlock();
        }
    }

    float getAverageConsumptionRate() {  // [ml/s]
        lock.readLock().lock();
        try {
            long dt = System.currentTimeMillis() - startTime;
            return 1000f*(initialWaterAmount - currentWaterAmount)/dt;
        } finally {
            lock.readLock().unlock();
        }
    }

    float getRemainingPart() {
        lock.readLock().lock();
        try {
            return getCurrentWaterAmount() / getInitialWaterAmount();
        } finally {
            lock.readLock().unlock();
        }
    }

    int getTimeTillEmpty() {  // [s]
        lock.readLock().lock();
        try {
            float acr = getAverageConsumptionRate();
            return (int) (currentWaterAmount/acr);
        } finally {
            lock.readLock().unlock();
        }
    }

    HPBMData getData() {
        lock.readLock().lock();
        try {
            return new HPBMData(
                getCurrentConsumptionRate(),
                getAverageConsumptionRate(),
                getRemainingPart(),
                getTimeTillEmpty()
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    private void consume( float amount ) {
        if ( amount <= 0f ) return;
        if ( currentWaterAmount <= 0f ) return;
        lock.writeLock().lock();
        try {
            currentWaterAmount -= amount;
            consumedSinceLastMeasurement += amount;
            if (startTime == -1) {
                startTime = System.currentTimeMillis();
                measurementTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        currentConsumptionRate = consumedSinceLastMeasurement;
                        consumedSinceLastMeasurement = 0;
                    }
                }, 1000, 1000);
            } else if (currentWaterAmount <= 0f) {
                currentConsumptionRate = 0f;
                consumedSinceLastMeasurement = 0f;
                measurementTimer.cancel();
                startTime = -1;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private TimerTask createSimulatedConsumptionTask() {
        return new TimerTask() {
            @Override
            public void run() {
                consume( (float) (Math.random()*5) );
                consumptionTimer.schedule( createSimulatedConsumptionTask(), (int) (500+Math.random()*1500) );
            }
        };
    }

}
