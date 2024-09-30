/*
 * manage multiple Broker instances and ensure their coordination and synchronization
 */

import java.util.*;
import java.io.*;
import java.net.*;

public class brokerNetwork {
    private List<broker> brokers;

    public brokerNetwork() {
        this.brokers = new ArrayList<>();
    }

    public void addBroker(broker b) {
        brokers.add(b);
    }

    public void broadcastMessage(String message) {
        for (broker b : brokers) {
            for (Map.Entry<String, Socket> entry : b.subscriberSockets.entrySet()) {
                try {
                    messageHandler.sendMessage(entry.getValue(), message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public List<broker.Topic> getAllTopics() {
        List<broker.Topic> allTopics = new ArrayList<>();
        for (broker b : brokers) {
            allTopics.addAll(b.getAllTopics());
        }
        return allTopics;
    }
}
