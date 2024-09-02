import java.net.*;
import java.io.*;
import java.util.List;
import com.google.gson.Gson;

/**
 * ClientHandler.java
 * Handles client requests and sends responses to the client.
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private Gson gson;
    private Dictionary dictionary;
    private DictionaryServer server;

    public ClientHandler(Socket clientSocket, Dictionary dictionary, DictionaryServer server) {
        this.clientSocket = clientSocket;
        this.gson = new Gson();
        this.dictionary = dictionary;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            initializeStreams();
            handleClientRequests();
        } catch (IOException e) {
            server.log("Error handling client: " + e.getMessage());
        } finally {
            closeConnection();
            server.updateConnectedClients(-1);
        }
    }
    private void initializeStreams() throws IOException {
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(clientSocket.getOutputStream(), true);
    }

    private void handleClientRequests() throws IOException {
        String jsonRequest;
        while ((jsonRequest = in.readLine()) != null) {
            server.log("Received from client: " + jsonRequest);
            DictionaryRequest request = gson.fromJson(jsonRequest, DictionaryRequest.class);
            String response = processRequest(request);
            out.println(gson.toJson(new DictionaryResponse(response)));
            server.incrementRequestCount();
        }
    }

    private String processRequest(DictionaryRequest request) {
        switch (request.getAction()) {
            case "add":
                return handleAddRequest(request);
            case "query":
                return handleQueryRequest(request.getWord());
            case "delete":
                return handleDeleteRequest(request.getWord());
            case "append":
                return handleAppendRequest(request);
            case "update":
                return handleUpdateRequest(request);
            default:
                return "Unknown command";
        }
    }

    private String handleAddRequest(DictionaryRequest request) {
        if (Dictionary.query(request.getWord()) != null) {
            return "Word already exists";
        }
        Dictionary.add(request.getWord(), request.getDefinition());
        return "Word added successfully";
    }

    private String handleQueryRequest(String word) {
        List<String> results = Dictionary.query(word);
        String result = results != null && !results.isEmpty() ? String.join("\n", results) : null;
        return (result != null) ? result : "Word not found";
    }

    private String handleDeleteRequest(String word) {
        if (Dictionary.query(word) == null) {
            return "Word not found";
        }
        Dictionary.delete(word);
        return "Word deleted successfully";
    }

    private String handleAppendRequest(DictionaryRequest request) {
        List<String> currentDefinitions = Dictionary.query(request.getWord());
        String currentDefinition = currentDefinitions != null ? String.join("\n", currentDefinitions) : null;
        if (currentDefinition == null) {
            return "Word not found";
        } else {
            Dictionary.append(request.getWord(), request.getDefinition());
            return "Definition appended successfully";
        }
    }

    private String handleUpdateRequest(DictionaryRequest request) {
        List<String> currentDefinitions = Dictionary.query(request.getWord());
        if (currentDefinitions == null || currentDefinitions.isEmpty()) {
            return "Word not found";
        }
        if (!currentDefinitions.contains(request.getOldDefinition())) {
            return "Old definition does not exist";
        }
        boolean updated = Dictionary.update(request.getWord(), request.getOldDefinition(), request.getDefinition());
        if (updated) {
            return "Word updated successfully";
        } else {
            return "Failed to update word";
        }
    }

    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            System.out.println("Client disconnected");
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}