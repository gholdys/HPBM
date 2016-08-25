package hpbm.app.core;

public final class HPBMData {

    private final float currentConsumption; // [ml/s]
    private final float averageConsumption; // [ml/s]
    private final float remainingPart;
    private final int timeToEmpty;          // [s]

    public HPBMData(float currentConsumption, float averageConsumption, float remainingPart, int timeToEmpty) {
        this.currentConsumption = currentConsumption;
        this.averageConsumption = averageConsumption;
        this.remainingPart = remainingPart;
        this.timeToEmpty = timeToEmpty;
    }

    public float getAverageConsumption() {
        return averageConsumption;
    }

    public float getCurrentConsumption() {
        return currentConsumption;
    }

    public float getRemainingPart() {
        return remainingPart;
    }

    public int getTimeToEmpty() {
        return timeToEmpty;
    }

}
