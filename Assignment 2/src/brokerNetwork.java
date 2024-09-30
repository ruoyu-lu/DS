/*
 * manage multiple Broker instances and ensure their coordination and synchronization
 */

import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class brokerNetwork {
    private Map<Integer, broker> brokers;
    private static final int BROKER_COUNT = 3;

    public brokerNetwork() {
        this.brokers = new ConcurrentHashMap<>();
    }

    public void addBroker(broker b) {
        brokers.put(b.getPort(), b);
    }

    public void broadcastMessage(String topicId, String message) {
        for (broker b : brokers.values()) {
            b.broadcastToBrokers(topicId, message);
        }
    }

    public List<broker.Topic> getAllTopics() {
        List<broker.Topic> allTopics = new ArrayList<>();
        for (broker b : brokers.values()) {
            allTopics.addAll(b.getAllTopics());
        }
        return allTopics;
    }

    // Method to initialize the broker network
    public void initializeBrokerNetwork(String[] brokerAddresses) {
        if (brokerAddresses.length != BROKER_COUNT) {
            throw new IllegalArgumentException("Expected " + BROKER_COUNT + " broker addresses");
        }

        for (int i = 0; i < BROKER_COUNT; i++) {
            String[] addressParts = brokerAddresses[i].split(":");
            String ip = addressParts[0];
            int port = Integer.parseInt(addressParts[1]);
            broker b = new broker(port);
            addBroker(b);

            // Connect to other brokers
            for (int j = 0; j < BROKER_COUNT; j++) {
                if (i != j) {
                    String[] otherAddressParts = brokerAddresses[j].split(":");
                    String otherIp = otherAddressParts[0];
                    int otherPort = Integer.parseInt(otherAddressParts[1]);
                    b.connectToBroker(otherIp, otherPort);
                }
            }
        }
    }
}
