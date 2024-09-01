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