package com.logandhillon.typeofwar.entity;

import com.logandhillon.typeofwar.entity.core.Entity;
import com.logandhillon.typeofwar.resource.Colors;
import com.logandhillon.typeofwar.resource.Fonts;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The game statistics entity is a UI layer for the {@link com.logandhillon.typeofwar.scene.TypeOfWarScene} that shows
 * the current player's statistics.
 * <p>
 * The statistics are supplied by the parent scene, and are not retrieved automatically.
 *
 * @author Logan Dhillon
 * @see GameStatisticsEntity#updateStats(int, int, int, boolean)
 */
public class GameStatisticsEntity extends Entity {
    private static final Font FONT_WPM    = Font.font(Fonts.DM_MONO_MEDIUM, 48);
    private static final Font FONT_HEADER = Font.font(Fonts.DM_MONO_MEDIUM, 28);
    private static final Font FONT_BODY   = Font.font(Fonts.DM_MONO, 20);

    private static final int WPM_CHAR_WIDTH   = 29;
    private static final int BODY_LINE_HEIGHT = 36;

    private final float width;

    private float elapsedSeconds; // used to calculate time (min) for wpm
    private float updateTimer; // used to recalculate stats every second

    private float  wpm                 = 0;
    private String wpmDisplay          = "0";
    private String accuracyText        = "-";
    private String accuracyTextDisplay = "-";
    private String completionText      = "-";

    private boolean isWinning;
    private float   accuracy;
    private int     typedChars = 0;
    private int     wordCount;
    private int     correctWords;

    /**
     * Creates an entity at the specified position.
     *
     * @param x     x-position (from left)
     * @param y     y-position (from top)
     * @param width total entity width, including whitespace, excluding margin
     */
    public GameStatisticsEntity(float x, float y, float width) {
        super(x, y);
        this.width = width;

        // reset counters
        this.elapsedSeconds = 0;
        this.updateTimer = 0;
    }

    /**
     * Update loop for the statistics, which ticks the timers and recalculates WPM every second.
     *
     * @param dt the delta time: change in time (seconds) since the last frame
     */
    @Override
    public void onUpdate(float dt) {
        elapsedSeconds += dt;
        updateTimer += dt;

        // raw WPM is defined as (total characters / 5) per delta minutes
        int rawWpm = (int)(((typedChars + 2) / 5f) / (elapsedSeconds / 60f));
        // net WPM is a function of rWPM and accuracy
        wpm = rawWpm * accuracy;

        // only run the code in this block ONCE per second.
        if (updateTimer >= 1.0f) {
            updateTimer -= 1.0f;
            wpmDisplay = String.valueOf((int)wpm);
            accuracyTextDisplay = accuracyText;
        }
    }

    /**
     * Render loop for game statistics.
     * <p>
     * Displays the WPM and other typing related stats on the left, ranking stats on the right, and a message if the
     * player is done typing in the center.
     *
     * @param g the graphical context to render to.
     * @param x the x position to render the entity at
     * @param y the y position to render the entity at
     */
    @Override
    protected void onRender(GraphicsContext g, float x, float y) {
        g.setFill(Color.WHITE);
        g.setTextAlign(TextAlignment.LEFT);

        // left side stats
        g.setFont(FONT_WPM);
        g.fillText(wpmDisplay, x, y);

        g.setFont(FONT_HEADER);
        g.fillText("wpm", x + wpmDisplay.length() * WPM_CHAR_WIDTH + 16, y);

        g.setFont(FONT_BODY);
        g.fillText(accuracyTextDisplay, x, y + BODY_LINE_HEIGHT);
        g.fillText(completionText, x, y + 2 * BODY_LINE_HEIGHT);

        // right side stats
        g.setTextAlign(TextAlignment.RIGHT);

        g.setFont(FONT_HEADER);
        g.setFill(isWinning ? Colors.GOLD_GRADIENT : Color.RED);
        g.fillText(isWinning ? "You're in the lead!" : "Catch-up!", x + width, y); // TODO: impl.
    }

    @Override
    public void onDestroy() {

    }

    /**
     * Updates the current player statistics, calculates what they are, and stores them for rendering. Call this
     * whenever the statistics change.
     *
     * @param correctChars amount of correct characters the user has typed
     * @param typedChars   amount of total characters the user has typed in this session
     * @param correctWords net correct words the user has finished
     * @param isWinning    if the player's team is winning
     */
    public void updateStats(int correctChars, int typedChars, int correctWords, boolean isWinning) {
        this.typedChars = typedChars;
        this.accuracy = typedChars == 0 ? 0 : (float)correctChars / typedChars;
        this.accuracyText = (int)(accuracy * 100) + "% accuracy";
        this.correctWords = correctWords;
        this.completionText = this.wordCount == 0 ? "-" : correctWords + " words";
        this.isWinning = isWinning;
    }

    /**
     * Sets the word count of the current {@link SentenceEntity}. This should be called only when the sentence changes.
     *
     * @param wordCount new word count
     */
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    /**
     * Restarts the session, allowing updates to propagate and resetting the timer to 0.
     *
     * @apiNote Should be used whenever (a) the sentence changes or (b) the user starts typing for the first time (so
     * the dead time before they started typing isn't counted).
     */
    public void restartSession() {
        elapsedSeconds = 0;
    }

    public EndResultEntity toEndResultEntity(PlayerObject player) {
        return new EndResultEntity(getWpm(), getAccuracy(), getCorrectWords(), player);
    }

    public int getWpm() {
        return (int)wpm;
    }

    public int getAccuracy() {
        return Math.round(accuracy * 100);
    }

    public int getCorrectWords() {
        return correctWords;
    }
}
