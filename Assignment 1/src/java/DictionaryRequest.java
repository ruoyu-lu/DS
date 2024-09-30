/**
 * Ruoyu Lu
 * 1466195
 * This class is used to represent a dictionary request.
 * It contains the action, word, old definition, and new definition.
 */
public class DictionaryRequest {
    private String action;
    private String word;
    private String oldDefinition;
    private String definition;

    public DictionaryRequest(String action, String word, String oldDefinition, String definition) {
        this.action = action;
        this.word = word;
        this.oldDefinition = oldDefinition;
        this.definition = definition;
    }

    public String getAction() {
        return action;
    }

    public String getWord() {
        return word;
    }

    public String getOldDefinition() {
        return oldDefinition;
    }

    public String getDefinition() {
        return definition;
    }
}