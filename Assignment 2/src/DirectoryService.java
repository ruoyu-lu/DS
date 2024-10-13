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
    private int port;
    private String ip;
    private Map<String, BrokerInfo> activeBrokers;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private ScheduledExecutorService timeoutChecker;
    private Map<String, Long> lastHeartbeatTimes;

    private static final int HEARTBEAT_TIMEOUT = 15; // 15 seconds

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

    public DirectoryService(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.activeBrokers = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.lastHeartbeatTimes = new ConcurrentHashMap<>();
        this.timeoutChecker = Executors.newSingleThreadScheduledExecutor();
        startTimeoutChecker();
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
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName(ip));
            System.out.println("Directory Service started on " + ip + ":" + port);

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
            System.out.println("Sent broker list to client: " + broker.toString());
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

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("用法: java -jar directoryservice.jar ip:port");
            return;
        }

        String[] parts = args[0].split(":");
        if (parts.length != 2) {
            System.out.println("无效的 IP:Port 格式。请使用 ip:port");
            return;
        }

        String ip = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.out.println("无效的端口号。请提供一个有效的数字。");
            return;
        }

        DirectoryService directoryService = new DirectoryService(ip, port);
        directoryService.start();
    }
}
