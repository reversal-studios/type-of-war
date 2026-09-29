package com.logandhillon.typeofwar.entity;

import com.logandhillon.typeofwar.TypeOfWar;
import com.logandhillon.typeofwar.engine.GameScene;
import com.logandhillon.typeofwar.entity.core.BoundEntity;
import com.logandhillon.typeofwar.scene.TypeOfWarScene;
import com.logandhillon.typeofwar.resource.Fonts;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Arrays;
import java.util.Objects;

import static com.logandhillon.typeofwar.TypeOfWar.CANVAS_HEIGHT;

/**
 * A SentenceEntity is the entity responsible for displaying the sentence and user input. Handles keyboard input and
 * text validation.
 * <p>
 * Moreover, SentenceEntity communicates with its parent class, {@link TypeOfWarScene}, to update statistics within the
 * scene. This entity can only be used within a {@link TypeOfWarScene}.
 *
 * @author Logan Dhillon
 * @see SentenceEntity#onKeyTyped(KeyEvent)
 * @see SentenceEntity#onKeyPressed(KeyEvent)
 */
public class SentenceEntity extends BoundEntity<TypeOfWarScene> {
    private static final int  CHAR_WIDTH  = 18;
    private static final int  LINE_HEIGHT = 28;
    private static final Font FONT        = Font.font(Fonts.DM_MONO, 32);

    private String[]        text;
    private StringBuilder[] input;
    private int             currentWord;
    private int             currentWordInLine = 1;
    private int             wordsInLine;
    private int             ignoredWords;
    private boolean         countWords        = true;

    private int typedChars;
    private int correctChars;
    private int correctWords;
    private int backspaces;

    private final float cursorY = y - (LINE_HEIGHT * 0.25f);

    private boolean isFirstKeyPress;
    private boolean isComplete;

    private static final AudioClip SFX_CORRECT   = new AudioClip(
            Objects.requireNonNull(SentenceEntity.class.getResource("/sound/click_1.wav")).toExternalForm());
    private static final AudioClip SFX_INCORRECT = new AudioClip(
            Objects.requireNonNull(SentenceEntity.class.getResource("/sound/error_1.wav")).toExternalForm());

    /**
     * Creates a new SentenceEntity at the provided coordinates.
     *
     * @param x distance from left
     * @param y distance from top
     *
     * @apiNote {@link SentenceEntity#setText(String)} must be called before using this entity.
     */
    public SentenceEntity(float x, float y) {
        super(x, y);
    }

    @Override
    public void onUpdate(float dt) {
    }

    @Override
    public void onDestroy() {

    }

    /**
     * Renders the original sentence and the user's input, which is automatically handled by the event handlers within
     * this file.
     * <p>
     * The position of the characters within
     *
     * @param g the graphical context to render to.
     * @param x the x position to render the entity at
     * @param y the y position to render the entity at
     */
    @Override
    public void onRender(GraphicsContext g, float x, float y) {
        g.setFont(FONT);
        g.setTextAlign(TextAlignment.LEFT);

        float dx = 64; // left-margin of text
        float dy = (CANVAS_HEIGHT + 310) / 2f;
        float cursorX = dx - 2 * CHAR_WIDTH;

        // for each word
        for (int i = 0; i < text.length; i++) {
            if (i < ignoredWords) continue;
            if (dx > TypeOfWar.CANVAS_WIDTH - 128) {
                dx = 64;
                dy += 4 * CHAR_WIDTH;
                countWords = false;
            } else if (countWords) {
                wordsInLine++;
            }

            // do not render characters that go off the screen
            if (dy > CANVAS_HEIGHT) continue;

            // for each letter in each word
            for (int j = 0; j < Math.max(text[i].length(), input[i].length()); j++) {
                // if input is long enough
                if (j < input[i].length()) {
                    // if input is in word
                    if (j < text[i].length()) {
                        // white for correct character, red for incorrect character
                        g.setFill(text[i].charAt(j) == input[i].charAt(j) ? Color.WHITE : Color.RED);
                        g.fillText(String.valueOf(text[i].charAt(j)), dx, dy);
                    } else {
                        // fill dark red if text extends too long
                        g.setFill(Color.DARKRED);
                        g.fillText(String.valueOf(input[i].charAt(j)), dx, dy);
                    }

                    cursorX = dx;
                } else {
                    // show dark red if word current word is ahead of this word (thus word incomplete) otherwise gray
                    g.setFill(i >= currentWord ? Color.GRAY : Color.DARKRED);
                    g.fillText(String.valueOf(text[i].charAt(j)), dx, dy);
                }

                dx += CHAR_WIDTH;
            }

            dx += CHAR_WIDTH;
        }

        // handle moving across words (independent of char)
        if (input[currentWord].isEmpty()) {
            if (currentWord == 0) cursorX += CHAR_WIDTH;
            else cursorX += (text[currentWord - 1].length() - input[currentWord - 1].length() + 1) * CHAR_WIDTH;
        }

        g.setFill(Color.WHITE);
        g.fillRect(cursorX + CHAR_WIDTH, cursorY, 1, LINE_HEIGHT);
    }

    /**
     * Sets this entity's sentence text that must be typed out. This will also reset user input.
     *
     * @param text The new text
     *
     * @apiNote This entity must be attached to a parent BEFORE calling this method!
     */
    public void setText(String text) {
        this.text = text.split(" ");
        parent.setWordCount(this.text.length);

        // reset user input
        input = new StringBuilder[this.text.length];
        Arrays.setAll(input, i -> new StringBuilder());
        isFirstKeyPress = true;
        isComplete = false;

        currentWord = 0;

        // reset user stats
        correctChars = 0;
        typedChars = 0;
        correctWords = 0;
    }

    /**
     * Runs when any key is pressed; handles backspaces by deleting characters and decrementing words.
     *
     * @param e KeyEvent from {@link Scene#onKeyPressedProperty()}
     */
    public void onKeyPressed(KeyEvent e) {
        // don't allow updates if session is complete
        if (isComplete) return;

        // restart timer if this is first key press
        if (isFirstKeyPress) {
            parent.resetWPMTimer();
            isFirstKeyPress = false;
        }

        // handle backspace
        if (e.getCode() == KeyCode.BACK_SPACE) {
            backspaces++;
            // decrease correct word count if word was correct
            if (text[currentWord].contentEquals(input[currentWord])) {
                correctWords--;
            }

            // decrement word counters if current word is empty OR if this is the last word and it was full
            if (input[currentWord].isEmpty() && currentWord > 0) {
                currentWord--;
                currentWordInLine--;
            } else if (!input[currentWord].isEmpty()) {
                input[currentWord].deleteCharAt(input[currentWord].length() - 1);
            }
        }

        // update typing statistics
        parent.updateStats(correctChars, typedChars, correctWords);
    }

    /**
     * Runs when any key is typed; handles typed characters and new words (spaces)
     *
     * @param e KeyEvent from {@link Scene#onKeyTypedProperty()}
     */
    public void onKeyTyped(KeyEvent e) {
        // don't allow updates if session is complete
        if (isComplete) return;

        String c = e.getCharacter();

        // ignore blank/control characters
        if (c.isEmpty() || Character.isISOControl(c.charAt(0))) return;

        // handle spaces (new words); increment word counter only if current word isn't blank
        if (c.equals(" ")) {
            if (currentWordInLine - 1 == wordsInLine - 1) {
                ignoredWords += wordsInLine;
                wordsInLine = 0;
                currentWordInLine = 0;
                countWords = true;
            }
            if (input[currentWord].length() == text[currentWord].length()) {
                SFX_CORRECT.setVolume(0.1);
                SFX_CORRECT.play();

                if (backspaces > 0) {
                    backspaces--;
                } else {
                    correctChars++;
                    parent.sendCorrectKeyPress(); // TODO: fix in practice mode
                }
            }
            if (!input[currentWord].isEmpty() && currentWord + 1 < input.length) {
                currentWord++;// increment word counter LAST so we can do statistics checks
                currentWordInLine++;
            }
        } else {
            input[currentWord].append(c);
        }

        // handle the other characters
        if (input[currentWord].length() <= text[currentWord].length() // automatically fail if the word is too long
            && String.valueOf(text[currentWord].charAt(Math.max(input[currentWord].length() - 1, 0))).equals(c)) {
            SFX_CORRECT.setVolume(0.1);
            SFX_CORRECT.play();

            if (backspaces > 0) {
                backspaces--;
            } else {
                correctChars++;
                parent.sendCorrectKeyPress(); // TODO: fix in practice mode
            }
        } else if (!c.equals(" ")) /* don't play err sfx if it's a space */ {
            SFX_INCORRECT.setVolume(0.2);
            SFX_INCORRECT.play();
        }
        typedChars++;

        // increment correct word count if the input matches the sentence
        if (text[currentWord].contentEquals(input[currentWord])) correctWords++;
    }

    @Override
    public void onAttach(GameScene parent) {
        super.onAttach(parent);
        parent.addHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        parent.addHandler(KeyEvent.KEY_TYPED, this::onKeyTyped);
    }

    /**
     * Marking this as complete simply prevents input from being registered. You can also use this to freeze the input
     * field beforehand (i.e. for a countdown).
     */
    public void setComplete(boolean complete) {
        isComplete = complete;
    }
}
