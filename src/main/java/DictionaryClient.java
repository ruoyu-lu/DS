/**
 * DictionaryClient:
 * The main client class, responsible for initializing the client-side user interface, processing user
 * inputs, and sending requests to the server over the network. It also handles receiving responses from
 * the server and displaying them to the user.
 */

import java.io.*;
import java.net.*;
import javax.swing.*;

import com.google.gson.Gson;

public class DictionaryClient {
    private BufferedReader in;
    private PrintWriter out;
    private Socket socket;
    private Gson gson;

    public DictionaryClient(String host, int port) throws IOException {
        connectToServer(host, port);
        UserInterface gui = new UserInterface(this);
        this.gson = new Gson();
    }

    public static void main(String[] args) throws IOException {
        SwingUtilities.invokeLater(() -> {
            try {
                new DictionaryClient(Constant.SERVER_ADDRESS, Constant.SERVER_PORT);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Failed to connect to server: " + e.getMessage());
                System.exit(1);
            }
        });
    }

    private void connectToServer(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Connected to server");
    }

    public String add(String word, String definition) throws IOException {
        return sendRequest(new DictionaryRequest("add", word, null, definition));
    }

    public String query(String word) throws IOException {
        return sendRequest(new DictionaryRequest("query", word, null, null));
    }

    public String delete(String word) throws IOException {
        return sendRequest(new DictionaryRequest("delete", word, null, null));
    }

    public String append(String word, String definition) throws IOException {
        return sendRequest(new DictionaryRequest("append", word, null, definition));
    }

    public String update(String word, String oldDefinition, String newDefinition) throws IOException {
        return sendRequest(new DictionaryRequest("update", word, oldDefinition, newDefinition));
    }

    private String sendRequest(DictionaryRequest request) throws IOException {
        String jsonRequest = gson.toJson(request);
        out.println(jsonRequest);
        String jsonResponse = in.readLine();
        DictionaryResponse response = gson.fromJson(jsonResponse, DictionaryResponse.class);
        return response.getMessage();
    }

    public void close() throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (socket != null) socket.close();
    }
}