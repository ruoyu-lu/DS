/*
 * File: directoryService.java
 * Author: Ruoyu Lu
 * Student ID: 1466195
 *
 * Description:
 * This class implements the Directory Service for the distributed publish-subscribe system.
 * It maintains a list of active brokers and provides this information to publishers and subscribers.
 *
 * Main functionalities include:
 * 1. Maintaining a list of active brokers
 * 2. Handling broker registration and deregistration
 * 3. Responding to client requests for broker information
 * 4. Monitoring broker health through heartbeats
 * 5. Removing inactive brokers from the active list
 *
 * The Directory Service plays a crucial role in the system by enabling dynamic discovery
 * of brokers and facilitating fault tolerance by keeping track of broker availability.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class DirectoryService {
    private static final int PORT = 8000;
    private static final int HEARTBEAT_TIMEOUT = 15; // 15 seconds
    private final Map<String, BrokerInfo> activeBrokers;
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final ScheduledExecutorService timeoutChecker;
    private final Map<String, Long> lastHeartbeatTimes;

    public DirectoryService() {
        this.activeBrokers = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.lastHeartbeatTimes = new ConcurrentHashMap<>();
        this.timeoutChecker = Executors.newSingleThreadScheduledExecutor();
        startTimeoutChecker();
    }

    // Main method to run the DirectoryService
    public static void main(String[] args) {
        DirectoryService directoryService = new DirectoryService();
        directoryService.start();
    }

    private void startTimeoutChecker() {
        timeoutChecker.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            List<String> timeoutBrokers = new ArrayList<>();
            for (Map.Entry<String, Long> entry : lastHeartbeatTimes.entrySet()) {
                if (currentTime - entry.getValue() > HEARTBEAT_TIMEOUT * 1000) {
                    timeoutBrokers.add(entry.getKey());
                }
            }
            for (String brokerKey : timeoutBrokers) {
                BrokerInfo broker = activeBrokers.remove(brokerKey);
                lastHeartbeatTimes.remove(brokerKey);
                if (broker != null) {
                    System.out.println("Broker timed out and removed: " + broker);
                }
            }
        }, HEARTBEAT_TIMEOUT, HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Directory Service started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handles client connections (brokers, publishers, or subscribers)
    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String request = in.readLine();
            System.out.println("Received request: " + request);
            switch (request) {
                case "REGISTER":
                    registerBroker(in, out);
                    break;
                case "UNREGISTER":
                    unregisterBroker(in, out);
                    break;
                case "GET_BROKERS":
                    sendBrokerList(out);
                    printActiveBrokers();
                    break;
                case "HEARTBEAT":
                    handleHeartbeat(in);
                    break;
                default:
                    out.println("UNKNOWN_COMMAND");
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleHeartbeat(BufferedReader in) throws IOException {
        String ip = in.readLine();
        int port = Integer.parseInt(in.readLine());
        String key = ip + ":" + port;
        lastHeartbeatTimes.put(key, System.currentTimeMillis());
        System.out.println("Received heartbeat from broker: " + key);
    }

    private void registerBroker(BufferedReader in, PrintWriter out) throws IOException {
        String ip = in.readLine();
        int port = Integer.parseInt(in.readLine());
        BrokerInfo newBroker = new BrokerInfo(ip, port);

        String key = ip + ":" + port;
        if (!activeBrokers.containsKey(key)) {
            activeBrokers.put(key, newBroker);
            lastHeartbeatTimes.put(key, System.currentTimeMillis());
            System.out.println("Registered new broker: " + newBroker);
            out.println("SUCCESS");
            sendBrokerListToNewBroker(out);
        } else {
            System.out.println("Broker already registered: " + newBroker);
            out.println("ALREADY_REGISTERED");
        }
    }

    private void unregisterBroker(BufferedReader in, PrintWriter out) throws IOException {
        String ip = in.readLine();
        int port = Integer.parseInt(in.readLine());
        String key = ip + ":" + port;

        if (activeBrokers.remove(key) != null) {
            System.out.println("Unregistered broker: " + key);
            out.println("SUCCESS");
        } else {
            System.out.println("Broker not found: " + key);
            out.println("NOT_FOUND");
        }
    }

    private void sendBrokerList(PrintWriter out) {
        for (BrokerInfo broker : activeBrokers.values()) {
            out.println(broker.toString());
            System.out.println("Sent broker list to client: " + broker);
        }
        out.println("END");
    }

    private void printActiveBrokers() {
        System.out.println("Current active brokers:");
        for (BrokerInfo broker : activeBrokers.values()) {
            System.out.println(broker);
        }
        System.out.println("--------------------");
    }

    private void sendBrokerListToNewBroker(PrintWriter out) {
        out.println("BROKER_LIST");
        sendBrokerList(out);
    }

    private static class BrokerInfo {
        String ip;
        int port;

        // Constructor: Initializes a BrokerInfo object
        BrokerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        // Returns a string representation of the broker information
        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
