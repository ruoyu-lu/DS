/*
 * represent a subscriber client that communicates with the Broker through sockets.
 * provide methods to list topics, subscribe/unsubscribe to topics, and receive messages.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class subscriber {
    private String name;
    private Socket brokerSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Set<String> subscriptions;

    public subscriber(String name, String brokerAddress, int brokerPort) throws IOException {
        this.name = name;
        this.brokerSocket = new Socket(brokerAddress, brokerPort);
        this.out = new PrintWriter(brokerSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
        this.subscriptions = new HashSet<>();
        
        out.println("SUBSCRIBER");
        out.println(name);
    }

    public void listAllTopics() throws IOException {
        out.println("LIST_TOPICS");
        String response;
        while (!(response = in.readLine()).equals("END")) {
            System.out.println(response);
        }
    }

    public void subscribeTopic(String topicId) throws IOException {
        out.println("SUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = in.readLine();
        if (response.equals("SUCCESS")) {
            subscriptions.add(topicId);
            System.out.println("Subscribed to topic: " + topicId);
        } else {
            System.out.println("Failed to subscribe to topic: " + topicId);
        }
    }

    public void showCurrentSubscriptions() {
        System.out.println("Current subscriptions:");
        for (String topicId : subscriptions) {
            System.out.println(topicId);
        }
    }

    public void unsubscribeTopic(String topicId) throws IOException {
        out.println("UNSUBSCRIBE_TOPIC");
        out.println(topicId);
        String response = in.readLine();
        if (response.equals("SUCCESS")) {
            subscriptions.remove(topicId);
            System.out.println("Unsubscribed from topic: " + topicId);
        } else {
            System.out.println("Failed to unsubscribe from topic: " + topicId);
        }
    }

    public void startListening() {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void close() throws IOException {
        brokerSocket.close();
    }

    public static void main(String[] args) {
        String username = "DefaultSubscriber";
        String brokerIp = "localhost";
        int brokerPort = 8080;

        if (args.length == 3) {
            username = args[0];
            brokerIp = args[1];
            brokerPort = Integer.parseInt(args[2]);
        }

        try {
            subscriber sub = new subscriber(username, brokerIp, brokerPort);
            sub.startListening();
            sub.startConsole();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startConsole() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n1. List All Topics");
            System.out.println("2. Subscribe to Topic");
            System.out.println("3. Show Current Subscriptions");
            System.out.println("4. Unsubscribe from Topic");
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            try {
                switch (choice) {
                    case 1:
                        listAllTopics();
                        break;
                    case 2:
                        System.out.print("Enter topic ID to subscribe: ");
                        subscribeTopic(scanner.nextLine());
                        break;
                    case 3:
                        showCurrentSubscriptions();
                        break;
                    case 4:
                        System.out.print("Enter topic ID to unsubscribe: ");
                        unsubscribeTopic(scanner.nextLine());
                        break;
                    case 5:
                        close();
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}