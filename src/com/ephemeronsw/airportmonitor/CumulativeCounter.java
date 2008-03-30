package com.ephemeronsw.airportmonitor;

public class CumulativeCounter {
	public CumulativeCounter() {
		
	}
	
	public CumulativeCounter(long initialValue) {
		currentValue = lastRestartValue = lastValue = initialValue;
	}
	
	public void accumulateValue(long value) {
		if(value < lastValue) {
			lastRestartValue = currentValue;
			resetValue = 0;
		}
		
		currentValue = lastRestartValue + (value - resetValue);
		lastValue = value;
	}
	
	public void resetAtValue(long value) {
		resetValue = value;
		lastValue = value;
		currentValue = 0;
		lastRestartValue = 0;
	}
	
	private long lastValue = 0;
	private long lastRestartValue = 0;
	private long currentValue = 0;
	private long resetValue = 0;
	
	public long getCurrentValue() {
		return currentValue;
	}
}
