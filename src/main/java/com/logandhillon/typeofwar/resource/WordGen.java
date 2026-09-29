package com.logandhillon.typeofwar.resource;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Word generation creates random sentences for users to type in their game Originating from MonkeyType, a major source
 * of inspiration for Type Of War, the library used is a 200-word english-language library consisting of the most
 * commonly used words in the language.
 *
 * @author Logan Dhillon, Daniel Gisondi
 * @see <a href="https://monkeytype.com">MonkeyType.com</a>
 */
public class WordGen {
    private static final String WORDS_PATH = "/words/english.txt";

    /**
     * generates a random sentence with `length` words in it, separated by spaces
     *
     * @param length length of sentence
     *
     * @return generated sentence
     */
    public static String generateSentence(int length) throws IOException {
        try (InputStream file = WordGen.class.getResourceAsStream(WORDS_PATH)) {
            // if file not found, throw error

            if (file == null) {
                throw new FileNotFoundException("File not found:" + WORDS_PATH);
            }

            BufferedReader buffered = new BufferedReader(new InputStreamReader(file));
            // Convert text to lines -> list
            List<String> words = buffered.lines().toList();

            Random rand = new Random();
            StringBuilder sentence = new StringBuilder();

            // Picks length amount of word s from the word bank file and adds to sentence
            for (int i = 0; i < length; i++) {
                int randWord = rand.nextInt(words.size());
                sentence.append(words.get(randWord));
                if (i < length - 1) {
                    sentence.append(" ");
                }
            }

            return sentence.toString();
        }
    }

    /**
     * Loops a given sentence a specified amount of times.
     * <p>
     * Used for the illusion of infinity because sentence entity is too far gone for anything else and not even James
     * Gosling could fix it anymore.
     *
     * @param sentence sentence to loop
     * @param times    amount of times to loop sentence
     *
     * @return final string to give to {@link com.logandhillon.typeofwar.entity.SentenceEntity}
     */
    public static String loopSentence(String sentence, int times) {
        return String.join(" ", Collections.nCopies(times, sentence));
    }
}
