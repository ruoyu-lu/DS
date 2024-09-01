/**
 * Dictionary:
 * Encapsulates the core data structure managing the dictionary data. This includes functionalities
 * such as searching for entries, adding new entries, deleting entries, and updating them.
 * This class provides various methods that other classes (like ClientHandler) can call to perform
 * specific dictionary operations.
 */
import java.io.*;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Dictionary {
    private static ConcurrentHashMap<String, List<String>> dictionary;

    public Dictionary() {
        dictionary = new ConcurrentHashMap<>();
    }

    public void loadDictionary(String filename) throws IOException {
        Gson gson = new Gson();
        Type type = new TypeToken<ConcurrentHashMap<String, List<String>>>() {}.getType();
        try (Reader reader = new FileReader(filename)) {
            ConcurrentHashMap<String, List<String>> tempDict = gson.fromJson(reader, type);
            tempDict.forEach((key, value) -> dictionary.put(key.toLowerCase(), value)); // Convert all keys to lower case
        }
    }

    public void saveDictionary(String filename) throws IOException {
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(filename)) {
            gson.toJson(dictionary, writer);
        }
    }

    /**
     * Query for a word in the dictionary.
     *
     * @param word The word to search for.
     * @return The definitions of the word, or null if the word is not found.
     */
    public static List<String> query(String word) {
        word = word.toLowerCase();
        return dictionary.get(word);
    }

    /**
     * Add a new word and its definition to the dictionary.
     *
     * @param word       The word to add.
     * @param definition The definition of the word.
     */
    public static void add(String word, String definition) {
        word = word.toLowerCase();
        List<String> definitions = dictionary.computeIfAbsent(word, k -> new ArrayList<>());
        definitions.add(definition);
    }

    /**
     * Delete a word from the dictionary.
     *
     * @param word The word to delete.
     */
    public static void delete(String word) {
        word = word.toLowerCase();
        dictionary.remove(word);
    }

    /**
     * Append a new definition to an existing word.
     *
     * @param word       The word to append to.
     * @param definition The new definition to append.
     */
    public static void append(String word, String definition) {
        word = word.toLowerCase();
        List<String> definitions = dictionary.computeIfAbsent(word, k -> new ArrayList<>());
        definitions.add(definition);
    }

    /**
     * Update a specific definition of a word in the dictionary.
     *
     * @param word           The word to update.
     * @param oldDefinition  The old definition to replace.
     * @param newDefinition  The new definition to use.
     * @return               True if the update was successful, false otherwise.
     */
    public static boolean update(String word, String oldDefinition, String newDefinition) {
        word = word.toLowerCase();
        List<String> definitions = dictionary.get(word);
        if (definitions != null) {
            int index = definitions.indexOf(oldDefinition);
            if (index != -1) {
                definitions.set(index, newDefinition);
                return true;
            }
        }
        return false;
    }
}