package com.ephemeronsw.airportmonitor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UsageRecord {
	private static final long MAGIC = 0xDEADBEEFDEADBEEFL;
	
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
	
	public static UsageRecord readFromStream(DataInputStream stream) throws IOException {
		UsageRecord record = new UsageRecord();
		
		long magic = stream.readLong();
		if(magic != MAGIC)
			throw new IOException("Magic value is wrong, stream corrupted");
		
		record.setTimeCaptured(stream.readLong());
		record.setTransferredIn(stream.readLong());
		record.setTransferredOut(stream.readLong());
		record.setUptime(stream.readLong());
		
		return record;
	}
	
	public void writeToStream(DataOutputStream stream) throws IOException {
		stream.writeLong(MAGIC);
		stream.writeLong(timeCaptured);
		stream.writeLong(transferredIn);
		stream.writeLong(transferredOut);
		stream.writeLong(uptime);
	}
}
