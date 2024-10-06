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
        this.brokers = new HashMap<>();
    }

    public void addBroker(broker b) {
        brokers.put(b.getPort(), b);
    }

    public void publishMessage(String topicId, String message) {
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
            String brokerName = "broker" + (i + 1);
            String ip = addressParts[0];
            int port = Integer.parseInt(addressParts[1]);
            broker b = new broker(port);
            addBroker(b);

            // Connect to other brokers
            for (int j = 0; j < BROKER_COUNT; j++) {
                if (i != j) {
                    String[] otherAddressParts = brokerAddresses[j].split(":");
                    String otherBrokerName = "broker" + (j + 1);
                    String otherIp = otherAddressParts[0];
                    int otherPort = Integer.parseInt(otherAddressParts[1]);
                    b.connectToBroker(otherBrokerName, otherIp, otherPort);
                }
            }
        }
    }

    public static void main(String[] args) {
        brokerNetwork network = new brokerNetwork();
        String[] brokerAddresses = {"localhost:8080", "localhost:8081", "localhost:8082"};

        for (int i = 0; i < BROKER_COUNT; i++) {
            String[] addressParts = brokerAddresses[i].split(":");
            String brokerName = "broker" + (i + 1);
            String ip = addressParts[0];
            int port = Integer.parseInt(addressParts[1]);
            broker b = new broker(port);
            network.addBroker(b);

            // Connect to other brokers
            for (int j = 0; j < BROKER_COUNT; j++) {
                if (i != j) {
                    String[] otherAddressParts = brokerAddresses[j].split(":");
                    String otherBrokerName = "broker" + (j + 1);
                    String otherIp = otherAddressParts[0];
                    int otherPort = Integer.parseInt(otherAddressParts[1]);
                    b.connectToBroker(otherBrokerName, otherIp, otherPort);
                }
            }
        }
    }
}
