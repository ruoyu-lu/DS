/*
 * This class is the broker class that will handle the messages and the topics
 */

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.UUID;

public class broker {
    private static final int MAX_PUBLISHERS = 5;
    private static final int MAX_SUBSCRIBERS = 10;

    private int port;
    private Map<String, Topic> topics;
    private Map<String, Socket> publisherSockets;
    private Map<String, Socket> subscriberSockets;
    private ExecutorService executorService;
    private Map<String, Socket> otherBrokers;
    private ExecutorService connectionExecutor;
    private Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private Set<String> queriedBrokers = new HashSet<>();

    public broker(int port) {
        this.port = port;
        this.topics = new ConcurrentHashMap<>();
        this.publisherSockets = new ConcurrentHashMap<>();
        this.subscriberSockets = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(MAX_PUBLISHERS + MAX_SUBSCRIBERS);
        this.otherBrokers = new ConcurrentHashMap<>();
        this.connectionExecutor = Executors.newCachedThreadPool();
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
            String clientType = reader.readLine();

            if ("BROKER".equals(clientType)) {
                handleBrokerConnection(clientSocket, reader);
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

    private void handleBrokerConnection(Socket brokerSocket, BufferedReader reader) throws IOException {
        String brokerName = reader.readLine();
        otherBrokers.put(brokerName, brokerSocket);
        System.out.println("Connected to broker: " + brokerName);

        String request;
        while ((request = reader.readLine()) != null) {
            switch (request) {
                case "SYNC_TOPIC":
                    String syncTopicId = reader.readLine();
                    String topicName = reader.readLine();
                    String publisherName = reader.readLine();
                    if (!topics.containsKey(syncTopicId)) {
                        topics.put(syncTopicId, new Topic(syncTopicId, topicName, publisherName));
                        System.out.println("Synced new topic: " + syncTopicId + " - " + topicName);
                    }
                    break;
                case "BROADCAST_MESSAGE":
                    String broadcastTopicId = reader.readLine();
                    String message = reader.readLine();
                    String messageId = reader.readLine();
                    String sourcePort = reader.readLine();
                    if (!processedMessages.contains(messageId)) {
                        processedMessages.add(messageId);
                        System.out.println("Received broadcast message for topic " + broadcastTopicId + ": " + message);
                        handleMessageBroadcast(broadcastTopicId, message, sourcePort, messageId);
                        // 处理消息，例如发送给订阅者
                        Topic topic = topics.get(broadcastTopicId);
                        if (topic != null) {
                            for (String subscriber : topic.subscribers) {
                                try {
                                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                                    if (subscriberSocket != null && subscriberSocket.isConnected()) {
                                        messageHandler.sendMessage(subscriberSocket, message);
                                    }
                                } catch (IOException e) {
                                    System.out.println("Error sending message to subscriber: " + subscriber);
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    break;
                case "SHOW_SUBSCRIBER_COUNT":
                    String showTopicId = reader.readLine();
                    Topic topic = topics.get(showTopicId);
                    PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                    if (topic != null) {
                        int totalCount = topic.subscribers.size();
                        // 请求其他 broker 的订阅者数量
                        for (Map.Entry<String, Socket> entry : otherBrokers.entrySet()) {
                            String brokerKey = entry.getKey();
                            if (!queriedBrokers.contains(brokerKey)) {
                                try {
                                    Socket otherBrokerSocket = entry.getValue();
                                    otherBrokerSocket.setSoTimeout(5000); // 设置超时
                                    PrintWriter otherOut = new PrintWriter(otherBrokerSocket.getOutputStream(), true);
                                    BufferedReader otherIn = new BufferedReader(new InputStreamReader(otherBrokerSocket.getInputStream()));
                                    otherOut.println("GET_SUBSCRIBER_COUNT");
                                    otherOut.println(showTopicId);
                                    String brokerResponse = otherIn.readLine();
                                    if (brokerResponse != null && !brokerResponse.startsWith("ERROR")) {
                                        totalCount += Integer.parseInt(brokerResponse);
                                    }
                                    queriedBrokers.add(brokerKey);
                                } catch (IOException e) {
                                    System.out.println("Error getting subscriber count from broker: " + brokerKey);
                                    e.printStackTrace();
                                    queriedBrokers.add(brokerKey); // 将出错的 broker 也添加到 queriedBrokers 中
                                }
                            }
                        }
                        out.println(totalCount);
                    } else {
                        out.println("ERROR: Topic not found");
                    }
                    break;
                case "DELETE_TOPIC":
                    String delTopicId = reader.readLine();
                    deleteTopic(delTopicId);
                    break;
                case "GET_SUBSCRIBER_COUNT":
                    String getTopicId = reader.readLine();
                    topic = topics.get(getTopicId);
                    out = new PrintWriter(brokerSocket.getOutputStream(), true);
                    if (topic != null) {
                        out.println(String.valueOf(topic.subscribers.size()));
                    } else {
                        out.println("ERROR: Topic not found");
                    }
                    break;
            }
        }
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
                            // Request subscriber count from other brokers
                            for (Map.Entry<String, Socket> entry : otherBrokers.entrySet()) {
                                String brokerKey = entry.getKey();
                                if (!queriedBrokers.contains(brokerKey)) { // Check if already queried
                                    try {
                                        Socket brokerSocket = entry.getValue();
                                        PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                                        out.println("GET_SUBSCRIBER_COUNT");
                                        out.println(showTopicId);

                                        BufferedReader in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));

                                        // Use an ExecutorService to handle the read with a timeout
                                        ExecutorService executor = Executors.newSingleThreadExecutor();
                                        Future<String> future = executor.submit(() -> in.readLine());

                                        String brokerResponse = null;
                                        try {
                                            // Set the timeout duration as needed (e.g., 5 seconds)
                                            brokerResponse = future.get(1, TimeUnit.SECONDS);
                                        } catch (TimeoutException e) {
                                            System.out.println("Timed out waiting for response from broker: " + brokerKey);
                                            future.cancel(true);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        } finally {
                                            executor.shutdownNow();
                                        }

                                        if (brokerResponse != null) {
                                            System.out.println("Response from broker " + brokerKey + ": " + brokerResponse);
                                            if (!brokerResponse.startsWith("ERROR")) {
                                                totalCount += Integer.parseInt(brokerResponse);
                                            }
                                        }

                                        queriedBrokers.add(brokerKey); // Add to queried brokers set
                                    } catch (IOException e) {
                                        System.out.println("Error getting subscriber count from broker: " + brokerKey);
                                        e.printStackTrace();
                                    }
                                }
                                System.out.println("testtesttesttest");
                            }
                            System.out.println("hello world");
                            String count = showTopicId + "|" + topic.name + "|" + totalCount;
                            System.out.println("Sending response to publisher: " + count);
                            messageHandler.sendMessage(publisherSockets.get(publisherName), count);
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "END");  // 发送 END 表示结果发送完毕
                        } else {
                            System.out.println("Topic not found or not owned by this publisher");
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found or not owned by this publisher");
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "END");  // 发送 END 表示结果发送完毕
                        }
                        queriedBrokers.clear(); // 清空 queriedBrokers，以便下一次请求重新查询
                        break;
                    case "DELETE_TOPIC":
                        String delTopicId = reader.readLine();
                        deleteTopic(delTopicId);
                        messageHandler.sendMessage(publisherSockets.get(publisherName), "Topic deleted");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error handling publisher: " + publisherName);
            e.printStackTrace();
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
                        subscribeTopic(subTopicId, subscriberName);
                        break;
                    case "UNSUBSCRIBE_TOPIC":
                        String unsubTopicId = reader.readLine();
                        unsubscribeTopic(unsubTopicId, subscriberName);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
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
        for (Map.Entry<String, Socket> entry : otherBrokers.entrySet()) {
            try {
                Socket brokerSocket = entry.getValue();
                PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                out.println("SHOW_SUBSCRIBER_COUNT");
                out.println(topicId);
            } catch (IOException e) {
                System.out.println("Error showing subscriber count to broker: " + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    // Delete a topic
    public void deleteTopic(String topicId) {
        Topic topic = topics.remove(topicId);
        if (topic != null) {
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    messageHandler.sendMessage(subscriberSocket, "Topic deleted: " + topic.name);
                } catch (IOException e) {

                    e.printStackTrace();     }
            }
        }
    }

    // Subscribe to a topic
    public void subscribeTopic(String topicId, String subscriberName) {
        Topic topic = topics.get(topicId);
        if (topic != null) {
            topic.subscribers.add(subscriberName);
            showSubscriberCount(topicId);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "SUCCESS|" + topic.name + "|" + topic.publisherName + "|" + topicId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "FAILED|Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Unsubscribe from a topic
    public void unsubscribeTopic(String topicId, String subscriberName) {
        System.out.println("Attempting to unsubscribe " + subscriberName + " from topic " + topicId);
        Topic topic = topics.get(topicId);
        if (topic != null) {
            boolean removed = topic.subscribers.remove(subscriberName);
            System.out.println("Subscriber removed from topic: " + removed);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, removed ? "SUCCESS" : "FAILED|Not subscribed to this topic");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Topic not found: " + topicId);
            try {
                Socket subscriberSocket = subscriberSockets.get(subscriberName);
                messageHandler.sendMessage(subscriberSocket, "FAILED: Topic not found");
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        if (args.length < 1) {
            System.out.println("用法: java -jar broker.jar <port> [-b <broker_ip_1:port1> <broker_ip_2:port2> ...]");
            return;
        }

        int port = Integer.parseInt(args[0]);
        broker brokerInstance = new broker(port);

        if (args.length > 2 && args[1].equals("-b")) {
            for (int i = 2; i < args.length; i++) {
                String[] brokerInfo = args[i].split(":");
                if (brokerInfo.length != 2) {
                    System.out.println("错误的 broker 信息格式: " + args[i]);
                    continue;
                }
                String ip = brokerInfo[0];
                int brokerPort = Integer.parseInt(brokerInfo[1]);
                brokerInstance.connectToBroker("broker" + (i - 1), ip, brokerPort);
            }
        }

        System.out.println("Broker starting on port " + port);
        brokerInstance.start();
    }

    public void connectToBroker(String brokerName, String ip, int port) {
        System.out.println("等待连接到 broker " + brokerName + " at " + ip + ":" + port);
        connectionExecutor.submit(() -> {
            while (true) {
                try {
                    Socket socket = new Socket(ip, port);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println("BROKER");
                    out.println(this.port); // 发送自己的端口号作为标识
                    otherBrokers.put(brokerName, socket);
                    System.out.println("成功连接到 broker " + brokerName + " at " + ip + ":" + port);

                    // 连接成功后，同步现有的topics
                    for (Topic topic : topics.values()) {
                        out.println("SYNC_TOPIC");
                        out.println(topic.id);
                        out.println(topic.name);
                        out.println(topic.publisherName);
                    }

                    break;
                } catch (IOException e) {
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
        for (Map.Entry<String, Socket> entry : otherBrokers.entrySet()) {
            try {
                Socket brokerSocket = entry.getValue();
                PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                out.println("SYNC_TOPIC");
                out.println(topicId);
                out.println(topicName);
                out.println(publisherName);
            } catch (IOException e) {
                System.out.println("Error broadcasting new topic to broker: " + entry.getKey());
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
            for (Map.Entry<String, Socket> entry : otherBrokers.entrySet()) {
                try {
                    Socket brokerSocket = entry.getValue();
                    PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                    out.println("BROADCAST_MESSAGE");
                    out.println(topicId);
                    out.println(message);
                    out.println(messageId);
                    out.println(sourcePort);
                } catch (IOException e) {
                    System.out.println("Error broadcasting new message to broker: " + entry.getKey());
                    e.printStackTrace();
                }
            }
        }
    }
}