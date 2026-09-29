package com.logandhillon.typeofwar.scene;

import com.logandhillon.typeofwar.TypeOfWar;
import com.logandhillon.typeofwar.engine.GameScene;
import com.logandhillon.typeofwar.entity.*;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.List;
import java.util.Objects;

import static com.logandhillon.typeofwar.TypeOfWar.CANVAS_HEIGHT;
import static com.logandhillon.typeofwar.TypeOfWar.CANVAS_WIDTH;
import static com.logandhillon.typeofwar.resource.Colors.BG_LOSING;
import static com.logandhillon.typeofwar.resource.Colors.BG_WINNING;

/**
 * The main game loop for Type of War.
 *
 * @author Logan Dhillon
 */
public class TypeOfWarScene extends GameScene {
    private static final Logger LOG = LoggerContext.getContext().getLogger(TypeOfWarScene.class);

    protected final GameStatisticsEntity stats;
    protected final TypeOfWar            game;
    protected final RopeEntity           rope;

    private   boolean isWinning = true;
    protected boolean isCountdownOver;

    private static final MediaPlayer BG_MUSIC = new MediaPlayer(new Media(
            Objects.requireNonNull(SentenceEntity.class.getResource("/sound/bgMusic1.mp3")).toExternalForm()));

    public TypeOfWarScene(TypeOfWar game, List<PlayerObject> team1, List<PlayerObject> team2, String sentenceText,
                          float multiplier) {
        this.game = game;
        stats = new GameStatisticsEntity(64, 144, CANVAS_WIDTH - 128);
        addEntity(stats);

        SentenceEntity sentence = new SentenceEntity(CANVAS_WIDTH / 2f, (CANVAS_HEIGHT + 300) / 2f);
        addEntity(sentence);
        sentence.setText(sentenceText);
        sentence.setComplete(true);

        rope = new RopeEntity(64, CANVAS_HEIGHT);
        for (var p: team1) rope.addPlayer(p, RopeEntity.Team.LEFT);
        for (var p: team2) rope.addPlayer(p, RopeEntity.Team.RIGHT);
        rope.setMultiplier(multiplier);
        addEntity(rope);

        isCountdownOver = false;
        addEntity(new CountdownEntity(CANVAS_WIDTH / 2f, 140, () -> {
            sentence.setComplete(false);
            isCountdownOver = true;
        }));
    }

    @Override
    protected void render(GraphicsContext g) {
        // background
        g.setFill(isWinning ? BG_WINNING : BG_LOSING);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // render all other entities
        super.render(g);
    }

    /**
     * Propagates an update in the {@link GameStatisticsEntity} instance.
     *
     * @param correctChars amount of correct characters the user has typed
     * @param typedChars   amount of total characters the user has typed in this session
     * @param correctWords net correct words the user has finished
     */
    public void updateStats(int correctChars, int typedChars, int correctWords) {
        stats.updateStats(correctChars, typedChars, correctWords, isWinning);
    }

    /**
     * Sets the word count in the {@link GameStatisticsEntity} instance.
     *
     * @param wordCount new word count of the sentence
     */
    public void setWordCount(int wordCount) {
        stats.setWordCount(wordCount);
    }

    /**
     * Resets the timer used for calculating WPM in the {@link GameStatisticsEntity}.
     */
    public void resetWPMTimer() {
        LOG.info("Resetting WPM timer");
        stats.restartSession();
    }

    /**
     * Handles a correct key press
     *
     * @throws IllegalStateException if there is no active server or client
     */
    public void sendCorrectKeyPress() {
        if (!isCountdownOver) return;

        boolean isServer = game.sendCorrectKeyPress();
        if (isServer) moveRope(true); // server host is always on team 1
        // if a client sent a key press, wait for the server to tell us to move the rope.
    }

    @Override
    public Scene build(Stage stage) {
        BG_MUSIC.setVolume(0.3);
        BG_MUSIC.play();
        return super.build(stage);
    }

    @Override
    public void discard(Scene scene) {
        BG_MUSIC.stop();
        super.discard(scene);
    }

    public void moveRope(boolean team1) {
        if (team1) rope.moveRopeL();
        else rope.moveRopeR();

        this.isWinning =
                rope.getWinningTeam() == (game.getWinningTeam() == 0 ? RopeEntity.Team.LEFT : RopeEntity.Team.RIGHT);
    }

    /**
     * Checks the active {@link TypeOfWar.NetworkRole} and, if it is a SERVER, signals a game end to connected clients.
     *
     * @param winningTeam the team# of the winner
     */
    public void signalGameEnd(int winningTeam) {
        game.setEndGameStats(stats);
        game.signalGameEnd(winningTeam);
    }

    public GameStatisticsEntity getStats() {
        return stats;
    }
}
