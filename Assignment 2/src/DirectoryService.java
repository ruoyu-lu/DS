import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

//提供一个集中化的注册系统，记录所有活跃的代理信息。
//代理启动时将自身的 IP 和端口号注册到 Directory Service。
//代理下线时从 Directory Service 中移除。
//代理通过 Directory Service 获取其他代理的信息。
//发布者/订阅者通过查询 Directory Service，获取当前可用的代理列表，然后连接到其中一个代理。

public class DirectoryService {
    private static final int PORT = 8000;
    private Map<String, BrokerInfo> activeBrokers;
    private ServerSocket serverSocket;
    private ExecutorService executorService;

    private static class BrokerInfo {
        String ip;
        int port;

        BrokerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }

    public DirectoryService() {
        this.activeBrokers = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
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
                    break;
                default:
                    out.println("UNKNOWN_COMMAND");
            }
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerBroker(BufferedReader in, PrintWriter out) throws IOException {
        String ip = in.readLine();
        int port = Integer.parseInt(in.readLine());
        BrokerInfo newBroker = new BrokerInfo(ip, port);
        
        String key = ip + ":" + port;
        if (!activeBrokers.containsKey(key)) {
            activeBrokers.put(key, newBroker);
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
        }
        out.println("END");
    }

    private void sendBrokerListToNewBroker(PrintWriter out) {
        out.println("BROKER_LIST");
        sendBrokerList(out);
    }

    public static void main(String[] args) {
        DirectoryService directoryService = new DirectoryService();
        directoryService.start();
    }
}
