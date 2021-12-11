/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

//import com.sun.javafx.image.IntPixelGetter;
import core.*;
import input.StandardEventsReader;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Modified class that creates a report where each row is made of:
 * current simulation time and an array with the number of connected hosts for each AccessPoint
 * and also the sum of the array elements = number of connected nodes at a specific time
 *
 * There are two possible parameters: granularity and accessPoint to select only one element of the array
 */
public class EventLogReport extends Report
	implements ConnectionListener, MessageListener {

	/** Extra info for message relayed event ("relayed"): {@value} */
	public static final String MESSAGE_TRANS_RELAYED = "R";
	/** Extra info for message relayed event ("delivered"): {@value} */
	public static final String MESSAGE_TRANS_DELIVERED = "D";
	/** Extra info for message relayed event ("delivered again"): {@value} */
	public static final String MESSAGE_TRANS_DELIVERED_AGAIN = "A";


	public static final String SPECIFYACCESSPOINT = "accessPoint";
	public static final String GRANULARITY = "granularity";
	// to compute the max connection per ap report
	public int[] maxConnections = new int[18];
	// actual number of connections per ap
	public int[] numberConnections = new int[18];
	// number of access point for which we want to extract data
	private int accessPointNumber = -1;
	private int granularity = 1;
	public double simTime = 0.0;
	public int totalHostsConnected = 0;

	public EventLogReport() {
		super();
		Settings settings = getSettings();
		// fill both arrays with 0 since at the beginning no nodes are connected
		Arrays.fill(numberConnections,0);
		Arrays.fill(maxConnections, 0);

		if(settings.contains(SPECIFYACCESSPOINT)) {
			accessPointNumber = settings.getInt(SPECIFYACCESSPOINT);
		}
		if(settings.contains(GRANULARITY)) {
			granularity = settings.getInt(GRANULARITY);
		}
	}
	/**
	 * Processes a log event by writing a line to the report file
	 * @param action The action as a string
	 * @param host1 First host involved in the event (if any, or null)
	 * @param host2 Second host involved in the event (if any, or null)
	 * @param message The message involved in the event (if any, or null)
	 * @param extra Extra info to append in the end of line (if any, or null)
	 */
	private synchronized void processEvent(final String action, final DTNHost host1,
			final DTNHost host2, final Message message, final String extra) {
		String mod1, mod2;

		if(getSimTime() == simTime) {
			if (host1.toString().contains("AccessPoint")) {
				mod1 = host1.toString().substring(12, 14);
				if(extra.equals("up"))
					numberConnections[Integer.parseInt(mod1)] += 1;
				else
					numberConnections[Integer.parseInt(mod1)] -= 1;
			}
			else if (host2.toString().contains("AccessPoint")) {
				mod2 = host2.toString().substring(12, 14);
				if(extra.equals("up"))
					numberConnections[Integer.parseInt(mod2)] += 1;
				else
					numberConnections[Integer.parseInt(mod2)] -= 1;
			}
		}
		else {
			totalHostsConnected = Arrays.stream(numberConnections).sum();

			// code used only for maxConnections report
			//for(int i=0; i<18; i++)
			//	if(numberConnections[i] > maxConnections[i])
			//		maxConnections[i] = numberConnections[i];


			if(simTime % granularity == 0) {
				if(accessPointNumber!= -1)
					// APx_granularity.txt
					write(simTime + " " + numberConnections[accessPointNumber]);
				else
					// sum.txt
					write(simTime + " " + totalHostsConnected);
					// average_lecture.txt
					//write(simTime + " " + (numberConnections[0]+numberConnections[14]+numberConnections[16])/3);
					// average_offices.txt
					//write(simTime + " " + (numberConnections[1]+numberConnections[3]+numberConnections[10]+numberConnections[11]+numberConnections[15]+numberConnections[17])/6);
					// average_tutorial.txt
					//write(simTime + " " + (numberConnections[5]+numberConnections[7]+numberConnections[8]+numberConnections[13])/4);
					// maxConnections.txt
					//write(Arrays.toString(maxConnections));
			}

			simTime = getSimTime();
			totalHostsConnected = 0;
			processEvent(action, host1, host2, message, extra);
		}
		/*
		// standard event log
		write(getSimTime() + " " + action + " " + (host1 != null ? host1 : "")
				+ (host2 != null ? (" " + host2) : "")
				+ (message != null ? " " + message : "")
				+ (extra != null ? " " + extra : ""));

		 */
	}


	public void hostsConnected(DTNHost host1, DTNHost host2) {
		processEvent(StandardEventsReader.CONNECTION, host1, host2, null,
				StandardEventsReader.CONNECTION_UP);
	}

	public void hostsDisconnected(DTNHost host1, DTNHost host2) {
		processEvent(StandardEventsReader.CONNECTION, host1, host2, null,
				StandardEventsReader.CONNECTION_DOWN);
	}

	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		processEvent((dropped ? StandardEventsReader.DROP :
			StandardEventsReader.REMOVE), where, null, m, null);
	}

	public void messageTransferred(Message m, DTNHost from, DTNHost to,
			boolean firstDelivery) {
		String extra;
		if (firstDelivery) {
			extra = MESSAGE_TRANS_DELIVERED;
		}
		else if (to == m.getTo()) {
			extra = MESSAGE_TRANS_DELIVERED_AGAIN;
		}
		else {
			extra = MESSAGE_TRANS_RELAYED;
		}

		processEvent(StandardEventsReader.DELIVERED, from, to, m, extra);
	}

	public void newMessage(Message m) {
		processEvent(StandardEventsReader.CREATE, m.getFrom(), null, m, null);
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
		processEvent(StandardEventsReader.ABORT, from, to, m, null);
	}

	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
		processEvent(StandardEventsReader.SEND, from, to, m, null);
	}
}
