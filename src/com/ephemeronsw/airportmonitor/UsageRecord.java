package com.ephemeronsw.airportmonitor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UsageRecord {
	private static final short MAGIC = (short) 0xBEEF;

	private long timeCaptured = 0;
	private long uptime = 0;
	private long transferredIn = 0;
	private long transferredOut = 0;

	public UsageRecord(long timeCaptured, long uptime, long transferredIn, long transferredOut) {
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

		short magic = stream.readShort();
		if (magic != MAGIC)
			throw new IOException("Magic value is wrong, stream corrupted");

		record.setTimeCaptured(stream.readLong());
		record.setTransferredIn(readUnsignedInt(stream));
		record.setTransferredOut(readUnsignedInt(stream));
		record.setUptime(readUnsignedInt(stream));

		return record;
	}

	private static long readUnsignedInt(DataInputStream stream) throws IOException {
		int firstByte = (0x000000FF & ((int) stream.readByte()));
		int secondByte = (0x000000FF & ((int) stream.readByte()));
		int thirdByte = (0x000000FF & ((int) stream.readByte()));
		int fourthByte = (0x000000FF & ((int) stream.readByte()));
		
		long anUnsignedInt = ((long) (firstByte << 24 | secondByte << 16 | thirdByte << 8 | fourthByte)) & 0xFFFFFFFFL;

		return anUnsignedInt;
	}

	
	// usage per month in GB = 22 * (31 * 24 * 60 * 60 / 10) / 1024 / 1024 = 5.62 GB;
	public void writeToStream(DataOutputStream stream) throws IOException {
		stream.writeShort(MAGIC);
		stream.writeLong(timeCaptured);
		stream.writeInt((int) transferredIn);
		stream.writeInt((int) transferredOut);
		stream.writeInt((int) uptime);
	}
}
