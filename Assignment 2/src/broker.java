/*
 * This class is the broker class that will handle the messages and the topics
 */

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class broker {
    private static final int MAX_PUBLISHERS = 5;
    private static final int MAX_SUBSCRIBERS = 10;
    private static final int HEARTBEAT_INTERVAL = 5; // 5 seconds
    private static final String HEARTBEAT_MESSAGE = "HEARTBEAT";

    private String brokerIp;
    private int port;
    private Map<String, Topic> topics;
    private Map<String, Socket> publisherSockets;
    private Map<String, Socket> subscriberSockets;
    private ExecutorService executorService;
    private Map<Integer, BrokerConnection> otherBrokers;
    private ExecutorService connectionExecutor;
    private Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private Set<Integer> queriedBrokers = new HashSet<>();
    private Map<String, CompletableFuture<Integer>> pendingSubscriberCountRequests = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeatExecutor;
    private String directoryServiceIp;
    private int directoryServicePort;

    private static class BrokerConnection {
        Socket socket;
        BufferedReader reader;
        PrintWriter writer;

        BrokerConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }
    }

    public broker(String brokerIp, int port) {
        this.brokerIp = brokerIp;
        this.port = port;
        this.topics = new ConcurrentHashMap<>();
        this.publisherSockets = new ConcurrentHashMap<>();
        this.subscriberSockets = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(MAX_PUBLISHERS + MAX_SUBSCRIBERS);
        this.otherBrokers = new ConcurrentHashMap<>();
        this.connectionExecutor = Executors.newCachedThreadPool();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    // Start the broker
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Broker started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleNewConnection(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle new client connection
    private void handleNewConnection(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            String clientType = reader.readLine();

            if ("BROKER".equals(clientType)) {
                int otherBrokerPort = Integer.parseInt(reader.readLine());
                writer.println("BROKER_CONNECTED");
                BrokerConnection brokerConn = new BrokerConnection(clientSocket);
                otherBrokers.put(otherBrokerPort, brokerConn);
                System.out.println("接受来自端口 " + otherBrokerPort + " 的 broker 连接");
                handleBrokerConnection(brokerConn);
            } else if ("PUBLISHER".equals(clientType)) {
                String clientName = reader.readLine();
                if (publisherSockets.size() < MAX_PUBLISHERS) {
                    publisherSockets.put(clientName, clientSocket);
                    handlePublisher(clientName, reader);
                } else {
                    clientSocket.close();
                }
            } else if ("SUBSCRIBER".equals(clientType)) {
                String clientName = reader.readLine();
                if (subscriberSockets.size() < MAX_SUBSCRIBERS) {
                    subscriberSockets.put(clientName, clientSocket);
                    handleSubscriber(clientName, reader);
                } else {
                    clientSocket.close();
                }
            } else {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleBrokerConnection(BrokerConnection brokerConn) throws IOException {
        String line;
        while ((line = brokerConn.reader.readLine()) != null) {
            String[] parts = line.split("\\|");
            String messageType = parts[0];
            switch (messageType) {
                case "SYNC_TOPIC":
                    handleSyncTopic(parts, brokerConn);
                    break;
                case "GET_SUBSCRIBER_COUNT":
                    handleGetSubscriberCount(parts, brokerConn);
                    break;
                case "SUBSCRIBER_COUNT":
                    handleSubscriberCountResponse(parts);
                    break;
                case "BROADCAST_MESSAGE":
                    handleBroadcastMessage(parts, brokerConn);
                    break;
                case "DELETE_TOPIC":
                    handleDeleteTopic(parts[1]);
                    break;
                case "SYNC_UNSUBSCRIBE":
                    handleSyncUnsubscribe(parts);
                    break;
                case "SYNC_UNSUBSCRIBE_ALL":
                    handleSyncUnsubscribeAll(parts[1]);
                    break;
            }
        }
    }

    private void handleSyncTopic(String[] parts, BrokerConnection brokerConn) {
        String syncTopicId = parts[1];
        String topicName = parts[2];
        String publisherName = parts[3];
        if (!topics.containsKey(syncTopicId)) {
            topics.put(syncTopicId, new Topic(syncTopicId, topicName, publisherName));
            System.out.println("Synced new topic: " + syncTopicId + " - " + topicName);
        }
    }

    private void handleGetSubscriberCount(String[] parts, BrokerConnection brokerConn) throws IOException {
        String topicId = parts[1];
        String requestId = parts[2]; // Extract the requestId
        int count = getLocalSubscriberCount(topicId);
        brokerConn.writer.println("SUBSCRIBER_COUNT|" + topicId + "|" + count + "|" + requestId);
    }

    private void handleSubscriberCountResponse(String[] parts) {
        String topicId = parts[1];
        int count = Integer.parseInt(parts[2]);
        String requestId = parts[3];
    
        CompletableFuture<Integer> future = pendingSubscriberCountRequests.remove(requestId);
        if (future != null) {
            future.complete(count);
        } else {
            System.out.println("Received response for unknown requestId: " + requestId);
        }
    }

    private void handleBroadcastMessage(String[] parts, BrokerConnection brokerConn) throws IOException {
        String topicId = parts[1];
        String message = parts[2];
        String messageId = parts[3];
        String sourcePort = parts[4];
        if (!processedMessages.contains(messageId)) {
            processedMessages.add(messageId);
            System.out.println("Received broadcast message for topic " + topicId + ": " + message);
            handleMessageBroadcast(topicId, message, sourcePort, messageId);
            // 处理消息，例如发送给订阅者
            Topic topic = topics.get(topicId);
            if (topic != null) {
                for (String subscriber : topic.subscribers) {
                    try {
                        Socket subscriberSocket = subscriberSockets.get(subscriber);
                        if (subscriberSocket != null && subscriberSocket.isConnected()) {
                            PrintWriter out = new PrintWriter(subscriberSocket.getOutputStream(), true);
                            out.println(message);
                        }
                    } catch (IOException e) {
                        System.out.println("Error sending message to subscriber: " + subscriber);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private int getLocalSubscriberCount(String topicId) {
        Topic topic = topics.get(topicId);
        return topic != null ? topic.subscribers.size() : 0;
    }

    // Handle publisher requests
    private void handlePublisher(String publisherName, BufferedReader reader) {
        try {
            String request;
            while ((request = reader.readLine()) != null) {
                switch (request) {
                    case "CREATE_TOPIC":
                        String topicId = reader.readLine();
                        String topicName = reader.readLine();
                        String response = createTopic(topicId, topicName, publisherName);
                        messageHandler.sendMessage(publisherSockets.get(publisherName), response);
                        break;
                    case "PUBLISH_MESSAGE":
                        String msgTopicId = reader.readLine();
                        String message = reader.readLine();
                        publishMessage(msgTopicId, message, publisherName);
                        break;
                        case "SHOW_SUBSCRIBER_COUNT":
                        System.out.println("Received SHOW_SUBSCRIBER_COUNT request from publisher: " + publisherName);
                        String showTopicId = reader.readLine();
                        System.out.println("Requested topic ID: " + showTopicId);
                        Topic topic = topics.get(showTopicId);
                        if (topic != null && topic.publisherName.equals(publisherName)) {
                            int totalCount = topic.subscribers.size();
                            System.out.println("Local subscriber count: " + totalCount);
                            System.out.println("Other broker count: " + otherBrokers.size());
                    
                            List<CompletableFuture<Integer>> futures = new ArrayList<>();
                    
                            for (Map.Entry<Integer, BrokerConnection> entry : otherBrokers.entrySet()) {
                                int brokerPort = entry.getKey();
                                BrokerConnection brokerConn = entry.getValue();
                    
                                String requestId = UUID.randomUUID().toString();
                                CompletableFuture<Integer> future = new CompletableFuture<>();
                                pendingSubscriberCountRequests.put(requestId, future);
                    
                                brokerConn.writer.println("GET_SUBSCRIBER_COUNT|" + showTopicId + "|" + requestId);
                                futures.add(future);
                            }
                    
                            // Wait for all futures to complete
                            try {
                                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
                            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                System.out.println("Error waiting for subscriber counts: " + e.getMessage());
                            }
                    
                            for (CompletableFuture<Integer> future : futures) {
                                try {
                                    totalCount += future.get();
                                } catch (InterruptedException | ExecutionException e) {
                                    System.out.println("Error getting subscriber count from future: " + e.getMessage());
                                }
                            }
                    
                            response = showTopicId + " " + topic.name + " " + totalCount;
                            System.out.println("Sending response to publisher: " + response);
                            messageHandler.sendMessage(publisherSockets.get(publisherName), response);
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "END");
                        } else {
                            System.out.println("Topic not found or not owned by this publisher");
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found or not owned by this publisher");
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "END");
                        }
                        break;
                    case "DELETE_TOPIC":
                        String delTopicId = reader.readLine();
                        String deleteResponse = deleteTopic(delTopicId, publisherName);
                        messageHandler.sendMessage(publisherSockets.get(publisherName), deleteResponse);
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling publisher: " + publisherName);
            e.printStackTrace();
        } finally {
            handlePublisherDisconnection(publisherName);
        }
    }

    // Handle subscriber requests
    private void handleSubscriber(String subscriberName, BufferedReader reader) {
        try {
            String request;
            while ((request = reader.readLine()) != null) {
                switch (request) {
                    case "LIST_TOPICS":
                        StringBuilder topicList = new StringBuilder();
                        for (Topic topic : topics.values()) {
                            topicList.append(topic.id).append("|")
                                    .append(topic.name).append("|")
                                    .append(topic.publisherName).append("\n");
                        }
                        messageHandler.sendMessage(subscriberSockets.get(subscriberName), topicList.toString() + "END");
                        break;
                    case "SUBSCRIBE_TOPIC":
                        String subTopicId = reader.readLine();
                        Topic topic = topics.get(subTopicId);
                        if (topic != null) {
                            topic.subscribers.add(subscriberName);
                            messageHandler.sendMessage(subscriberSockets.get(subscriberName), 
                                "SUCCESS|" + topic.name + "|" + topic.publisherName + "|" + subTopicId);
                        } else {
                            messageHandler.sendMessage(subscriberSockets.get(subscriberName), 
                                "FAILED|Topic not found");
                        }
                        break;
                    case "UNSUBSCRIBE_TOPIC":
                        String unsubTopicId = reader.readLine();
                        Topic unsubTopic = topics.get(unsubTopicId);
                        if (unsubTopic != null) {
                            boolean removed = unsubTopic.subscribers.remove(subscriberName);
                            messageHandler.sendMessage(subscriberSockets.get(subscriberName), 
                                removed ? "SUCCESS" : "FAILED|Not subscribed to this topic");
                        } else {
                            messageHandler.sendMessage(subscriberSockets.get(subscriberName), 
                                "FAILED|Topic not found");
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling subscriber: " + subscriberName);
            e.printStackTrace();
        } finally {
            handleSubscriberDisconnection(subscriberName);
        }
    }

    // Create a new topic
    public String createTopic(String topicId, String topicName, String publisherName) {
        if (topics.containsKey(topicId)) {
            return "ERROR: Topic ID already exists";
        }
        topics.put(topicId, new Topic(topicId, topicName, publisherName));
        handleTopicBroadcast(topicId, topicName, publisherName);
        return "SUCCESS: Topic created with ID: " + topicId;
    }

    // Publish a message to a topic
    public void publishMessage(String topicId, String message, String publisherName) {
        Topic topic = topics.get(topicId);
        if (topic != null) {
            if (!topic.publisherName.equals(publisherName)) {
                System.out.println("Error: Publisher " + publisherName + " is not authorized to publish to topic " + topicId);
                try {
                    messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: You are not authorized to publish to this topic");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
            String formattedMessage = messageHandler.formatMessage(topicId, topic.name, publisherName, message);
            System.out.println("Publishing message to topic " + topicId + ": " + formattedMessage);
            handleMessageBroadcast(topicId, formattedMessage, String.valueOf(this.port), null);
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    messageHandler.sendMessage(subscriberSocket, formattedMessage);
                } catch (IOException e) {
                    System.out.println("Error sending message to subscriber: " + subscriber);
                    e.printStackTrace();
                }
            }
            // 发送成功消息给发布者
            try {
                Socket publisherSocket = publisherSockets.get(publisherName);
                messageHandler.sendMessage(publisherSocket, "SUCCESS: Message published");
            } catch (IOException e) {
                System.out.println("Error sending success message to publisher: " + publisherName);
                e.printStackTrace();
            }
        } else {
            System.out.println("Topic not found: " + topicId);
            try {
                messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void showSubscriberCount(String topicId) {
        for (Map.Entry<Integer, BrokerConnection> entry : otherBrokers.entrySet()) {
                BrokerConnection brokerConn = entry.getValue();
                brokerConn.writer.println("SHOW_SUBSCRIBER_COUNT|" + topicId);
            
        }
    }

    // Delete a topic
    public String deleteTopic(String topicId, String publisherName) {
        Topic topic = topics.get(topicId);
        if (topic == null) {
            return "ERROR: Topic not found";
        }
        if (!topic.publisherName.equals(publisherName)) {
            return "ERROR: You don't have permission to delete this topic";
        }
        
        topics.remove(topicId);
        // Notify subscribers
        for (String subscriber : topic.subscribers) {
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriber);
                messageHandler.sendMessage(subscriberSocket, "TOPIC_DELETED|" + topicId + "|" + topic.name);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        // Broadcast delete operation to other brokers
        handleTopicDeleteBroadcast(topicId);
        
        return "SUCCESS: Topic deleted with ID: " + topicId;
    }

    private void handleSyncUnsubscribe(String[] parts) {
        String topicId = parts[1];
        String subscriberName = parts[2];
        Topic topic = topics.get(topicId);
        if (topic != null) {
            topic.subscribers.remove(subscriberName);
            System.out.println("Synced unsubscribe: " + subscriberName + " from topic " + topicId);
        }
    }

    // Inner class to represent a Topic
    public static class Topic {
        String id;
        String name;
        String publisherName;
        Set<String> subscribers;

        Topic(String id, String name, String publisherName) {
            this.id = id;
            this.name = name;
            this.publisherName = publisherName;
            this.subscribers = new HashSet<>();
        }
    }

    // Main method to run the broker
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("用法: java -jar broker.jar brokerIP:brokerPort directoryServiceIp:directoryServicePort");
            return;
        }

        String[] brokerInfo = args[0].split(":");
        String[] directoryServiceInfo = args[1].split(":");

        if (brokerInfo.length != 2 || directoryServiceInfo.length != 2) {
            System.out.println("无效的参数格式。请使用 IP:Port 格式。");
            return;
        }

        String brokerIp = brokerInfo[0];
        int brokerPort = Integer.parseInt(brokerInfo[1]);
        String directoryServiceIp = directoryServiceInfo[0];
        int directoryServicePort = Integer.parseInt(directoryServiceInfo[1]);

        broker brokerInstance = new broker(brokerIp, brokerPort);
        brokerInstance.registerWithDirectoryService(directoryServiceIp, directoryServicePort);

        System.out.println("Broker 正在启动，IP: " + brokerIp + ", 端口: " + brokerPort);
        brokerInstance.start();
    }

    public void connectToBroker(String brokerName, String ip, int port) {
        System.out.println("尝试连接到 broker " + brokerName + " 地址: " + ip + ":" + port);
        connectionExecutor.submit(() -> {
            while (true) {
                try {
                    Socket socket = new Socket(ip, port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
                    // 发送 BROKER 标识和自己的端口号
                    out.println("BROKER");
                    out.println(this.port);
                    
                    // 等待对方的确认
                    String response = in.readLine();
                    if ("BROKER_CONNECTED".equals(response)) {
                        BrokerConnection brokerConn = new BrokerConnection(socket);
                        otherBrokers.put(port, brokerConn);
                        System.out.println("成功连接到 broker " + brokerName + " 地址: " + ip + ":" + port);

                        // 连接成功后，同步现有的 topics
                        for (Topic topic : topics.values()) {
                            brokerConn.writer.println("SYNC_TOPIC|" + topic.id + "|" + topic.name + "|" + topic.publisherName);
                        }
                        
                        // 启动一个新线程来处理来自这个 broker 的消息
                        new Thread(() -> {
                            try {
                                handleBrokerConnection(brokerConn);
                            } catch (IOException e) {
                                System.out.println("处理 broker 连接时发生错误: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }).start();
                        
                        break;
                    } else {
                        System.out.println("连接到 broker " + brokerName + " 失败: 未收到预期的响应");
                        socket.close();
                    }
                } catch (IOException e) {
                    System.out.println("连接到 broker " + brokerName + " 失败: " + e.getMessage());
                    // 连接失败，等待一段时间后重试
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    public void handleTopicBroadcast(String topicId, String topicName, String publisherName) {
        for (BrokerConnection brokerConn : otherBrokers.values()) {
            try {
                brokerConn.writer.println("SYNC_TOPIC|" + topicId + "|" + topicName + "|" + publisherName);
            } catch (Exception e) {
                System.out.println("Error broadcasting new topic to broker");
                e.printStackTrace();
            }
        }
    }
    public void handleMessageBroadcast(String topicId, String message, String sourcePort, String messageId) {
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        if (!processedMessages.contains(messageId)) {
            processedMessages.add(messageId);
            System.out.println("Broadcasting message to other brokers: " + message);
            for (BrokerConnection brokerConn : otherBrokers.values()) {
                try {
                    brokerConn.writer.println("BROADCAST_MESSAGE|" + topicId + "|" + message + "|" + messageId + "|" + sourcePort);
                } catch (Exception e) {
                    System.out.println("Error broadcasting new message to broker");
                    e.printStackTrace();
                }
            }
        }
    }

    public void handleTopicDeleteBroadcast(String topicId) {
        for (BrokerConnection brokerConn : otherBrokers.values()) {
            try {
                brokerConn.writer.println("DELETE_TOPIC|" + topicId);
            } catch (Exception e) {
                System.out.println("Error broadcasting topic deletion to broker");
                e.printStackTrace();
            }
        }
    }

    private void handleDeleteTopic(String topicId) {
        Topic topic = topics.remove(topicId);
        if (topic != null) {
            // Notify subscribers
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    messageHandler.sendMessage(subscriberSocket, "TOPIC_DELETED|" + topicId + "|" + topic.name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Deleted topic: " + topicId + " due to broadcast from another broker");
    }

    // Handle publisher disconnection
    private void handlePublisherDisconnection(String publisherName) {
        System.out.println("Publisher disconnected: " + publisherName);
        publisherSockets.remove(publisherName);
        
        // Remove all topics created by this publisher
        List<String> topicsToRemove = new ArrayList<>();
        for (Map.Entry<String, Topic> entry : topics.entrySet()) {
            if (entry.getValue().publisherName.equals(publisherName)) {
                topicsToRemove.add(entry.getKey());
            }
        }
        
        for (String topicId : topicsToRemove) {
            deleteTopic(topicId, publisherName);
        }
    }

    // Handle subscriber disconnection
    private void handleSubscriberDisconnection(String subscriberName) {
        System.out.println("Subscriber disconnected: " + subscriberName);
        subscriberSockets.remove(subscriberName);
        
        // Unsubscribe from all topics
        for (Topic topic : topics.values()) {
            topic.subscribers.remove(subscriberName);
        }
        
        // Broadcast unsubscribe to other brokers
        for (BrokerConnection brokerConn : otherBrokers.values()) {
            try {
                brokerConn.writer.println("SYNC_UNSUBSCRIBE_ALL|" + subscriberName);
            } catch (Exception e) {
                System.out.println("Error broadcasting unsubscribe all to broker");
                e.printStackTrace();
            }
        }
    }

    private void handleSyncUnsubscribeAll(String subscriberName) {
        for (Topic topic : topics.values()) {
            topic.subscribers.remove(subscriberName);
        }
        System.out.println("Synced unsubscribe all for subscriber: " + subscriberName);
    }

    private void registerWithDirectoryService(String directoryServiceIp, int directoryServicePort) {
        this.directoryServiceIp = directoryServiceIp;
        this.directoryServicePort = directoryServicePort;
        try (Socket socket = new Socket(directoryServiceIp, directoryServicePort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER");
            out.println(this.brokerIp);
            out.println(this.port);

            String response = in.readLine();
            if ("SUCCESS".equals(response)) {
                System.out.println("成功注册到 Directory Service");
                String brokerList = in.readLine();
                if ("BROKER_LIST".equals(brokerList)) {
                    connectToOtherBrokers(in);
                }
                startHeartbeat(); // Start sending heartbeats after successful registration
            } else {
                System.out.println("注册到 Directory Service 失败: " + response);
            }
        } catch (IOException e) {
            System.out.println("连接到 Directory Service 时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToOtherBrokers(BufferedReader in) throws IOException {
        String line;
        while (!(line = in.readLine()).equals("END")) {
            String[] brokerInfo = line.split(":");
            if (brokerInfo.length == 2) {
                String ip = brokerInfo[0];
                int port = Integer.parseInt(brokerInfo[1]);
                if (port != this.port) {
                    connectToBroker("broker" + port, ip, port);
                }
            }
        }
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try (Socket socket = new Socket(directoryServiceIp, directoryServicePort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(HEARTBEAT_MESSAGE);
                out.println(this.brokerIp);
                out.println(this.port);
            } catch (IOException e) {
                System.out.println("Failed to send heartbeat: " + e.getMessage());
            }
        }, 0, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
    }
}