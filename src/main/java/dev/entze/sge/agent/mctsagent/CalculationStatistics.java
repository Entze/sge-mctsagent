package dev.entze.sge.agent.mctsagent;

import java.util.concurrent.TimeUnit;

public class CalculationStatistics {

  private static final long DEFAULT_TARGET_SIMULATIONS = 128L;
  private static final long DEFAULT_TOTAL_TIME = 30L;
  private static final TimeUnit DEFAULT_TOTAL_TIME_TIMEUNIT = TimeUnit.SECONDS;

  private long simulationsDone;
  private long targetSimulations;
  private long startTime;
  private long endTime;

  private long averageNanosPerSimulation;
  private long measuredSimulationsDone;

  public CalculationStatistics(long targetSimulations, long startTime, TimeUnit startTimeTimeUnit,
      long totalTime, TimeUnit totalTimeTimeUnit) {
    this.targetSimulations = targetSimulations;
    this.startTime = startTimeTimeUnit.toNanos(startTime);
    this.endTime = startTime + totalTimeTimeUnit.toNanos(totalTime);
    this.simulationsDone = 0L;
    this.averageNanosPerSimulation = 0L;
    this.measuredSimulationsDone = 0L;
  }

  public CalculationStatistics() {
    this(DEFAULT_TARGET_SIMULATIONS, System.nanoTime(), TimeUnit.NANOSECONDS, DEFAULT_TOTAL_TIME,
        DEFAULT_TOTAL_TIME_TIMEUNIT);
  }

  public CalculationStatistics(long targetSimulations) {
    this(targetSimulations, System.nanoTime(), TimeUnit.NANOSECONDS, DEFAULT_TOTAL_TIME,
        DEFAULT_TOTAL_TIME_TIMEUNIT);
  }

  public void resetStartTime(long startTime, TimeUnit timeUnit) {
    this.startTime = timeUnit.toNanos(startTime);
  }

  public void resetStartTime() {
    this.resetStartTime(System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  public void resetSimulationsDone() {
    this.simulationsDone = 0L;
  }

  public void resetAverageNanosPerSimulation() {
    measuredSimulationsDone = 0L;
  }

  public void setTargetSimulations(long targetSimulations) {
    this.targetSimulations = targetSimulations;
  }

  public void resetTotalTime(long totalTime, TimeUnit timeUnit) {
    this.endTime = timeUnit.toNanos(startTime + totalTime);
  }

  public void resetEndTime(long endTime, TimeUnit timeUnit) {
    this.endTime = timeUnit.toNanos(endTime);
  }

  public long timeLeft(TimeUnit timeUnit) {
    return timeUnit.convert(endTime - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  public long nanosLeft() {
    return this.timeLeft(TimeUnit.NANOSECONDS);
  }

  public long timeElapsed(TimeUnit timeUnit) {
    return timeUnit.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
  }

  public long nanosElapsed() {
    return this.timeElapsed(TimeUnit.NANOSECONDS);
  }

  public long totalTime(TimeUnit timeUnit) {
    return timeUnit.convert(endTime - startTime, TimeUnit.NANOSECONDS);
  }

  public long totalNanos() {
    return totalTime(TimeUnit.NANOSECONDS);
  }

  public long nanosPerSimulation() {
    if (simulationsDone <= 0) {
      return totalNanos() + 1;
    }
    return nanosElapsed() / simulationsDone;
  }

  public long getSimulationsDone() {
    return simulationsDone;
  }

  public long getTargetSimulations() {
    return targetSimulations;
  }

  public long simulationsLeft() {
    return targetSimulations - simulationsDone;
  }

  public long estimatedSimulations() {
    return nanosLeft() / nanosPerSimulation();
  }

  public void incrementSimulationsDone(long by) {
    simulationsDone += by;
  }

  public void incrementSimulationsDone() {
    this.incrementSimulationsDone(1);
  }

  public void measureNanosPerSimulation() {
    averageNanosPerSimulation =
        (measuredSimulationsDone * averageNanosPerSimulation + nanosPerSimulation()) /
            measuredSimulationsDone + simulationsDone;
    measuredSimulationsDone += simulationsDone;
  }

  public long getAverageNanosPerSimulation() {
    return averageNanosPerSimulation;
  }
}
