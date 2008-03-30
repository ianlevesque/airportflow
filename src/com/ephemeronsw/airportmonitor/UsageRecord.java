package com.ephemeronsw.airportmonitor;

import java.io.Serializable;

public class UsageRecord implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private long timeCaptured = 0;
	private long uptime = 0;
	private long transferredIn = 0;
	private long transferredOut = 0;

	public UsageRecord(long timeCaptured, long uptime, long transferredIn,
			long transferredOut) {
		this.timeCaptured = timeCaptured;
		this.uptime = uptime;
		this.transferredIn = transferredIn;
		this.transferredOut = transferredOut;
	}
	
	public UsageRecord() {
	}

	public long getTimeCaptured() {
		return timeCaptured;
	}

	public void setTimeCaptured(long timeCaptured) {
		this.timeCaptured = timeCaptured;
	}

	public long getUptime() {
		return uptime;
	}

	public void setUptime(long uptime) {
		this.uptime = uptime;
	}

	public long getTransferredIn() {
		return transferredIn;
	}

	public void setTransferredIn(long transferredIn) {
		this.transferredIn = transferredIn;
	}

	public long getTransferredOut() {
		return transferredOut;
	}

	public void setTransferredOut(long transferredOut) {
		this.transferredOut = transferredOut;
	}
}
