package com.ephemeronsw.airportmonitor;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class AirPortMonitor implements Runnable {

	private static final OID UPTIME_OID = new OID("1.3.6.1.2.1.1.3.0");
	private static final OID IN_OCTETS_OID = new OID("1.3.6.1.2.1.2.2.1.10.1");
	private static final OID OUT_OCTETS_OID = new OID("1.3.6.1.2.1.2.2.1.16.1");

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		configureLogging();
		
		AirPortMonitor monitor;
		try {
			monitor = new AirPortMonitor();
		} catch (IOException e) {
			Logger.getLogger(AirPortMonitor.class).error("Error starting", e);
			return;
		}
		
		monitor.run();
	}

	private static void configureLogging() {
		BasicConfigurator.configure();
	}

	private Logger logger = Logger.getLogger(getClass());
	private Snmp snmp;
	private PDU requestPDU;
	private CommunityTarget target;
	
	public AirPortMonitor() throws IOException {

		target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));
		Address targetAddress = GenericAddress.parse("udp:10.0.1.1/161");
		target.setAddress(targetAddress );
		target.setVersion(SnmpConstants.version2c);
		target.setRetries(2);
		target.setTimeout(1500);
		
		requestPDU = new PDU();
		requestPDU.add(new VariableBinding(OUT_OCTETS_OID)); // ifOutOctets (for gec0)
		requestPDU.add(new VariableBinding(IN_OCTETS_OID)); // ifInOctets (for gec0)
		requestPDU.add(new VariableBinding(UPTIME_OID)); // uptime
		requestPDU.setType(PDU.GET);
		
		DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
		snmp = new Snmp(transport);
		transport.listen();
	}

	public void run() {
		Timer loggingTimer = new Timer("Logging timer", false);
		
		loggingTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				logUsageNow();
			}
		}, 0, 10000);
	}
	
	public void logUsageNow() {
		try {
			ResponseEvent response = snmp.send(requestPDU, target);
			
			if(response.getResponse() != null) {
				UsageRecord record = new UsageRecord(System.currentTimeMillis(), 0, 0, 0);
				
				long uptimeTicks = -1;
				long usageIn = -1;
				long usageOut = -1;
				
				for(VariableBinding binding : response.getResponse().toArray()) {
					if(binding.getOid().equals(OUT_OCTETS_OID)) {
						logger.info("Out octets: " + binding.getVariable().toString());
						usageOut = binding.getVariable().toLong();
					} else if(binding.getOid().equals(IN_OCTETS_OID)) {
						logger.info("In octets: " + binding.getVariable().toString());
						usageIn = binding.getVariable().toLong();
					} else if(binding.getOid().equals(UPTIME_OID)) {
						logger.info("Uptime: " + binding.getVariable().toString());
						uptimeTicks = binding.getVariable().toLong();
					}
				}
				
				if(uptimeTicks == -1 || usageIn == -1 || usageOut == -1) {
					logger.error("One or more replies missing"); // FIXME: necessary?
				} else {
					record.setUptime(uptimeTicks * 10); // convert to milliseconds
					record.setTransferredIn(usageIn);
					record.setTransferredOut(usageOut);
				}

				storeRecord(record);
				processRecord(record);
			} else {
				logger.error("Request timed out");
			}
		} catch (IOException e) {
			logger.error("Error requesting interface information via SNMP", e);
		}
	}

	private void processRecord(UsageRecord record) {
		
	}

	private void storeRecord(UsageRecord record) {
		
	}

}
