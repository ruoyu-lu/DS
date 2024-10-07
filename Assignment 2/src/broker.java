/*
 * This class is the broker class that will handle the messages and the topics
 */

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.UUID;

public class broker {
    private static final int MAX_MESSAGE_LENGTH = 100;
    private static final int MAX_PUBLISHERS = 5;
    private static final int MAX_SUBSCRIBERS = 10;

    private int port;
    private Map<String, Topic> topics;
    private Map<String, Socket> publisherSockets;
    private Map<String, Socket> subscriberSockets;
    private ExecutorService executorService;
    private Map<String, Socket> otherBrokers;
    private ExecutorService connectionExecutor;

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
            if ("SYNC_TOPIC".equals(request)) {
                String topicId = reader.readLine();
                String topicName = reader.readLine();
                String publisherName = reader.readLine();
                syncTopic(topicId, topicName, publisherName);
            }
            // 处理其他类型的broker间通信...
        }
    }

    // Create a new topic
    public String createTopic(String topicId, String topicName, String publisherName) {
        if (topics.containsKey(topicId)) {
            return "ERROR: Topic ID already exists";
        }
        topics.put(topicId, new Topic(topicId, topicName, publisherName));
        broadcastNewTopic(topicId, topicName, publisherName);
        return "SUCCESS: Topic created with ID: " + topicId;
    }

    // Publish a message to a topic
    public void publishMessage(String topicId, String message, String publisherName) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            try {
                messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Message exceeds maximum length");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        Topic topic = topics.get(topicId);
        if (topic != null) {
            String formattedMessage = messageHandler.formatMessage(topicId, topic.name, publisherName, message);
            boolean allSent = true;
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    if (subscriberSocket != null && subscriberSocket.isConnected()) {
                        messageHandler.sendMessage(subscriberSocket, formattedMessage);
                    } else {
                        System.out.println("Subscriber socket is null or disconnected: " + subscriber);
                        allSent = false;
                    }
                } catch (IOException e) {
                    System.out.println("Error sending message to subscriber: " + subscriber);
                    e.printStackTrace();
                    allSent = false;
                }
            }
            System.out.println("Message published to topic " + topicId + ": " + formattedMessage);
            try {
                if (allSent) {
                    messageHandler.sendMessage(publisherSockets.get(publisherName), "SUCCESS");
                } else {
                    messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Some subscribers could not receive the message");
                }
            } catch (IOException e) {
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

    // Subscribe to a topic
    public void subscribeTopic(String topicId, String subscriberName) {
        Topic topic = topics.get(topicId);
        if (topic != null) {
            topic.subscribers.add(subscriberName);
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

    // Delete a topic
    public void deleteTopic(String topicId) {
        Topic topic = topics.remove(topicId);
        if (topic != null) {
            for (String subscriber : topic.subscribers) {
                try {
                    Socket subscriberSocket = subscriberSockets.get(subscriber);
                    messageHandler.sendMessage(subscriberSocket, "Topic deleted: " + topic.name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Get all available topics
    public List<Topic> getAllTopics() {
        return new ArrayList<>(topics.values());
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
                        // 移除这行，因为响应已经在 publishMessage 方法中发送
                        // messageHandler.sendMessage(publisherSockets.get(publisherName), "Message published");
                        break;
                    case "SHOW_SUBSCRIBER_COUNT":
                        String showTopicId = reader.readLine();
                        Topic topic = topics.get(showTopicId);
                        if (topic != null && topic.publisherName.equals(publisherName)) {
                            String count = showTopicId + "|" + topic.name + "|" + topic.subscribers.size() + "\n";
                            messageHandler.sendMessage(publisherSockets.get(publisherName), count + "END");
                        } else {
                            messageHandler.sendMessage(publisherSockets.get(publisherName), "ERROR: Topic not found or not owned by this publisher");
                        }
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

    // 添加一个公共方法来获取端口
    public int getPort() {
        return port;
    }

    // 将 connectToBroker 方法改为 public
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

    // 添加一个方法来广播消息到其他 broker
    public void broadcastToBrokers(String topicId, String message) {
        // TODO: 实现广播逻辑
        System.out.println("Broadcasting message for topic " + topicId + " to other brokers");
    }

    public void syncTopic(String topicId, String topicName, String publisherName) {
        if (!topics.containsKey(topicId)) {
            topics.put(topicId, new Topic(topicId, topicName, publisherName));
            System.out.println("Synced new topic: " + topicId + " - " + topicName);
        }
    }

    public void broadcastNewTopic(String topicId, String topicName, String publisherName) {
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
}