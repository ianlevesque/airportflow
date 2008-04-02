package com.ephemeronsw.airportmonitor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.DefaultServlet;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.resource.Resource;
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

	private static final String HISTORY_PREFIX = "history-";
	private static final SimpleDateFormat HISTORY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH");
	private static final OID UPTIME_OID = new OID("1.3.6.1.2.1.1.3.0");
	private static final OID IN_OCTETS_OID = new OID("1.3.6.1.2.1.2.2.1.10.1");
	private static final OID OUT_OCTETS_OID = new OID("1.3.6.1.2.1.2.2.1.16.1");

	private File historyDirectory = new File("./history/");

	CumulativeCounter totalIn = new CumulativeCounter();
	CumulativeCounter totalOut = new CumulativeCounter();

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

		try {
			monitor.loadHistory();
		} catch (Exception e) {
			Logger.getLogger(AirPortMonitor.class).error("Error loading history", e);
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
	private Server jettyServer;
	private long lastIn;
	private long lastOut;
	private long lastTime;
	private long lastInterval;

	public AirPortMonitor() throws IOException {

		target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));
		Address targetAddress = GenericAddress.parse("udp:10.0.1.1/161");
		target.setAddress(targetAddress);
		target.setVersion(SnmpConstants.version2c);
		target.setRetries(2);
		target.setTimeout(1500);

		requestPDU = new PDU();
		requestPDU.add(new VariableBinding(OUT_OCTETS_OID)); // ifOutOctets
		// (for gec0)
		requestPDU.add(new VariableBinding(IN_OCTETS_OID)); // ifInOctets (for
		// gec0)
		requestPDU.add(new VariableBinding(UPTIME_OID)); // uptime
		requestPDU.setType(PDU.GET);

		DefaultUdpTransportMapping transport = new DefaultUdpTransportMapping();
		snmp = new Snmp(transport);
		transport.listen();

		jettyServer = new Server(8080);
		Context context = new Context(jettyServer, "/", Context.SESSIONS);
		try {
			context.setBaseResource(Resource.newClassPathResource("/web/"));
		} catch(Exception e) {
			context.setResourceBase("./web/");
		}
		context.addServlet(new ServletHolder(new HttpServlet() {
			@Override
			protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
				resp.setContentType("text/javascript");
				PrintWriter writer = resp.getWriter();

				double limit = 200;
				double total = (totalIn.getCurrentValue() + totalOut.getCurrentValue()) / 1024.0 / 1024.0 / 1024.0;
				double upload = totalOut.getCurrentValue() / 1024.0 / 1024.0 / 1024.0;
				double download = totalIn.getCurrentValue() / 1024.0 / 1024.0 / 1024.0;

				DecimalFormat format = new DecimalFormat();
				format.setMaximumFractionDigits(3);
				format.setMinimumFractionDigits(3);

				long progress = (long) (total / limit * 100.0);
				writer.write("{ \"progress\" : \"" + progress + "%\", \"download\" : \"" + format.format(download) + " GB\", \"upload\" : \"" + format.format(upload)
						+ " GB\",  \"total\" : \"" + format.format(total) + " GB\", \"limit\" : \"" + format.format(limit) + " GB\", "
						+ "\"lastdown\" : \"" + format.format(lastIn / (lastInterval / 1000.0) / 1024.0) + " KB/s\", "    
						+ "\"lastup\" : \"" + format.format(lastOut / (lastInterval / 1000.0) / 1024.0) + " KB/s\" " +  
				"  }");
			}
		}), "/value");
		context.addServlet(new ServletHolder(new DefaultServlet()), "/*");
		try {
			jettyServer.start();
		} catch (Exception e) {
			logger.error("Error starting jetty server", e);
		}
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

			if (response.getResponse() != null) {
				UsageRecord record = new UsageRecord(System.currentTimeMillis(), 0, 0, 0);

				long uptimeTicks = -1;
				long usageIn = -1;
				long usageOut = -1;

				String uptimeStr = "";
				for (VariableBinding binding : response.getResponse().toArray()) {
					if (binding.getOid().equals(OUT_OCTETS_OID)) {
						usageOut = binding.getVariable().toLong();
					} else if (binding.getOid().equals(IN_OCTETS_OID)) {
						usageIn = binding.getVariable().toLong();
					} else if (binding.getOid().equals(UPTIME_OID)) {
						uptimeTicks = binding.getVariable().toLong();
						uptimeStr = binding.getVariable().toString();
					}
				}
				
				logger.debug("In octets: " + usageIn + " Out octets: " + usageOut + " Uptime: " + uptimeStr);

				if (uptimeTicks == -1 || usageIn == -1 || usageOut == -1) {
					logger.error("One or more replies missing"); // FIXME:
					// necessary?
				} else {
					record.setUptime(uptimeTicks * 10); // convert to
					// milliseconds
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
		long currentTime = record.getTimeCaptured();
		
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTimeInMillis(lastTime);
		int lastMonth = calendar.get(GregorianCalendar.MONTH);
		
		calendar.setTimeInMillis(currentTime);
		int thisMonth = calendar.get(GregorianCalendar.MONTH); 
		
		lastInterval = currentTime - lastTime;
		lastTime = currentTime;

		if(lastMonth != thisMonth && lastTime != 0) {
			totalIn.resetAtValue(record.getTransferredIn());
			totalOut.resetAtValue(record.getTransferredOut());
			logger.info("Resetting month!");
		} else {

			// FIXME: use Uptime value to correctly accumulate the very top of the
			// overflow (4GB)

			lastIn = totalIn.accumulateValue(record.getTransferredIn());
			lastOut = totalOut.accumulateValue(record.getTransferredOut());
		}
		
		double inGigabytes = totalIn.getCurrentValue() / 1024.0 / 1024.0 / 1024.0;
		double outGigabytes = totalOut.getCurrentValue() / 1024.0 / 1024.0 / 1024.0;

		DecimalFormat format = new DecimalFormat();
		format.setMaximumFractionDigits(3);
		format.setMinimumFractionDigits(3);

		logger.info("Total In: " + format.format(inGigabytes) + " GB, Total Out: " + format.format(outGigabytes) + " GB, Total Usage: " + format.format(inGigabytes + outGigabytes) + " GB");
	}

	private void storeRecord(UsageRecord record) throws IOException {
		if (historyDirectory.exists() == false)
			historyDirectory.mkdir();

		// find the right file, append the record
		String filename = HISTORY_PREFIX + HISTORY_DATE_FORMAT.format(new Date(record.getTimeCaptured()));

		DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(historyDirectory, filename), true)));
		record.writeToStream(outputStream);
		outputStream.close();
	}

	private void loadHistory() throws FileNotFoundException, IOException, ClassNotFoundException {
		if (!historyDirectory.exists())
			return;

		File[] historyFiles = historyDirectory.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				if (pathname.isDirectory())
					return false;

				return pathname.getName().startsWith("history");
			}
		});

		GregorianCalendar calendar = new GregorianCalendar();
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		long startOfMonth = calendar.getTimeInMillis();

		List<File> thisMonthFiles = new ArrayList<File>();

		boolean firstFileIsFirstOfMonth = false;

		// find this month's files
		for (File file : historyFiles) {
			logger.debug("History file: " + file.getName());
			try {
				long date = HISTORY_DATE_FORMAT.parse(file.getName().substring(HISTORY_PREFIX.length())).getTime();

				if (date >= startOfMonth) {
					thisMonthFiles.add(file);
				}

				if (date == startOfMonth) {
					firstFileIsFirstOfMonth = true;
				}
			} catch (ParseException e) {
			}
		}

		// sort by date
		Collections.sort(thisMonthFiles, new Comparator<File>() {
			public int compare(File o1, File o2) {
				try {
					long date1 = HISTORY_DATE_FORMAT.parse(o1.getName().substring(HISTORY_PREFIX.length())).getTime();
					long date2 = HISTORY_DATE_FORMAT.parse(o2.getName().substring(HISTORY_PREFIX.length())).getTime();
					return (int) (date1 - date2);
				} catch (ParseException e) {
					return 0;
				}
			}

		});

		// process
		if (thisMonthFiles.size() > 0) {
			File firstFile = thisMonthFiles.get(0);
			DataInputStream inputStream = null;
			try {
				logger.debug("Processing history file: " + firstFile.getName());

				inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(firstFile)));

				if (firstFileIsFirstOfMonth) {
					UsageRecord firstRecord = UsageRecord.readFromStream(inputStream);
					totalIn.resetAtValue(firstRecord.getTransferredIn());
					totalOut.resetAtValue(firstRecord.getTransferredOut());
				}

				processEntireStream(inputStream);
			} catch (Exception e) {
				logger.error("Error processing file: " + firstFile.getName(), e);
			} finally {
				if (inputStream != null) {
					inputStream.close();
					inputStream = null;
				}
			}

			for (int i = 1; i < thisMonthFiles.size(); i++) {
				logger.debug("Processing history file: " + thisMonthFiles.get(i).getName());
				try {
					inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(thisMonthFiles.get(i))));
					processEntireStream(inputStream);
				} catch (Exception e) {
					logger.error("Error processing file: " + thisMonthFiles.get(i).getName(), e);
				} finally {
					if (inputStream != null) {
						inputStream.close();
						inputStream = null;
					}
				}
			}
		}
	}

	private void processEntireStream(DataInputStream inputStream) throws IOException, ClassNotFoundException {
		try {
			while (true) {
				UsageRecord record = UsageRecord.readFromStream(inputStream);
				if (record == null)
					break;

				processRecord(record);
			}
		} catch (EOFException e) {
			// don't care
		}
	}

}
