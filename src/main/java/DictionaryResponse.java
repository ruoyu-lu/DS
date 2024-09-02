/**
 * DictionaryResponse class is used to send the response message to the client.
 * It has a single field message which is the response message.
 */
public class DictionaryResponse {
    private String message;

    public DictionaryResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
