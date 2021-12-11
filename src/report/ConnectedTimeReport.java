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
import java.util.HashMap;
import java.util.List;

/**
 * Reports a two column file (id, data), where:
 * id = name or identifier of a node
 * data = total time connected / total time of the simulation
 */

public class ConnectedTimeReport extends Report
        implements ConnectionListener, MessageListener {

    /**
     * Extra info for message relayed event ("relayed"): {@value}
     */
    public static final String MESSAGE_TRANS_RELAYED = "R";
    /**
     * Extra info for message relayed event ("delivered"): {@value}
     */
    public static final String MESSAGE_TRANS_DELIVERED = "D";
    /**
     * Extra info for message relayed event ("delivered again"): {@value}
     */
    public static final String MESSAGE_TRANS_DELIVERED_AGAIN = "A";

    public HashMap<String,Integer> totalConnectedTime = new HashMap<>();
    public boolean done = false;
    public int count1 = 0;

    public ConnectedTimeReport() {
        super();

        for(int i=0; i<100; i++)
            totalConnectedTime.put("s"+i,0);

        for(int i=118; i<148; i++)
            totalConnectedTime.put("e"+i,0);

        for(int i=148; i<158; i++)
            totalConnectedTime.put("o"+i,0);

    }


    /**
     * Processes a log event by writing a line to the report file
     *
     * @param action  The action as a string
     * @param host1   First host involved in the event (if any, or null)
     * @param host2   Second host involved in the event (if any, or null)
     * @param message The message involved in the event (if any, or null)
     * @param extra   Extra info to append in the end of line (if any, or null)
     */
    private void processEvent(final String action, final DTNHost host1,
                              final DTNHost host2, final Message message, final String extra) {

        if (!host1.toString().contains("AccessPoint")) {
            if (extra.equals("up"))
                totalConnectedTime.put(host1.toString(), totalConnectedTime.get(host1.toString())+1);
        } else if (!host2.toString().contains("AccessPoint")) {
            if (extra.equals("up"))
                totalConnectedTime.put(host2.toString(), totalConnectedTime.get(host2.toString())+1);
        }

        if(getSimTime()>43200 && !done) {
            for (String key : totalConnectedTime.keySet())
                write(key + " " + totalConnectedTime.get(key));
            done = true;
        }

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
        } else if (to == m.getTo()) {
            extra = MESSAGE_TRANS_DELIVERED_AGAIN;
        } else {
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
