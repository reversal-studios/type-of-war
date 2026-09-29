package com.logandhillon.typeofwar;

import com.logandhillon.typeofwar.engine.GameEngine;
import com.logandhillon.typeofwar.engine.GameScene;
import com.logandhillon.typeofwar.engine.disk.UserConfigManager;
import com.logandhillon.typeofwar.entity.EndResultEntity;
import com.logandhillon.typeofwar.entity.GameStatisticsEntity;
import com.logandhillon.typeofwar.entity.PlayerObject;
import com.logandhillon.typeofwar.networking.*;
import com.logandhillon.typeofwar.networking.proto.ConfigProto;
import com.logandhillon.typeofwar.networking.proto.EndGameProto;
import com.logandhillon.typeofwar.networking.proto.GameInitProto;
import com.logandhillon.typeofwar.resource.WordGen;
import com.logandhillon.typeofwar.scene.EndGameScene;
import com.logandhillon.typeofwar.scene.TypeOfWarScene;
import com.logandhillon.typeofwar.scene.component.MenuAlertScene;
import com.logandhillon.typeofwar.scene.component.MenuQuestionScene;
import com.logandhillon.typeofwar.scene.menu.JoinGameScene;
import com.logandhillon.typeofwar.scene.menu.LobbyGameScene;
import com.logandhillon.typeofwar.scene.menu.MainMenuScene;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;

/**
 * This is the main entrypoint for Type of War, handling low-level game engine code and GameScene management.
 *
 * @author Logan Dhillon
 * @see TypeOfWarScene
 */
public class TypeOfWar extends Application {
    private static final Logger LOG               = LoggerContext.getContext().getLogger(TypeOfWar.class);
    public static final  String GAME_NAME         = "Type of War";
    public static final  int    CANVAS_WIDTH      = 1280; // the width of the rendered canvas
    public static final  int    CANVAS_HEIGHT     = 720; // the height of the rendered canvas
    public static final  float  ASPECT_RATIO      = (float)CANVAS_WIDTH / CANVAS_HEIGHT;
    public static final  float  SCALING_TOLERANCE = 0.05f; // % to prefer maximizing size over aspect ratio

    // game engine
    private Stage     stage;
    private GameScene activeScene;

    // game state management
    private volatile int     team;
    private volatile boolean isInMenu;
    private          float   baseMultiplier;
    private          String  customSentence;

    // used only for end game
    private volatile EndGameProto.PlayerStats endGameStats;
    private volatile boolean                  isGameEndSignalled;
    private volatile int                      winningTeam;

    // networking
    private static GameServer       server;
    private static GameClient       client;
    private static ServerDiscoverer discoverer;

    private static ConfigProto.UserConfig userConfig;

    /**
     * Handles communication with JavaFX when this program is signalled to start.
     *
     * @param stage the primary stage for this application, provided by the JavaFX framework.
     */
    @Override
    public void start(Stage stage) {
        // rename thread to shorten logs
        Thread.currentThread().setName("FX");

        this.stage = stage;
        isInMenu = true;

        stage.setTitle(GAME_NAME);
        stage.setWidth(CANVAS_WIDTH);
        stage.setHeight(CANVAS_HEIGHT);
        stage.setMinWidth(CANVAS_WIDTH / 2f);
        stage.setMinHeight(CANVAS_HEIGHT / 2f);

        setScene(new MainMenuScene(this));
        stage.show();
    }

    /**
     * Handles bootstrap and launching the framework + engine.
     *
     * @param args command-line arguments to the Java program.
     *
     * @see TypeOfWar#start(Stage)
     */
    public static void main(String[] args) throws IOException {
        // load user config first
        userConfig = UserConfigManager.load();

        // then start the javafx program
        launch();

        // this runs AFTER the javafx window closes
        LOG.info("Program terminated, exiting cleanly");
        if (server != null) server.stop();
        if (client != null) client.close();
        terminateDiscoverer();
    }

    /**
     * Discards the currently active scene and replaces it with the provided one.
     *
     * @param scene the GameScene to switch
     */
    public void setScene(GameScene scene) {
        activeScene = GameEngine.setScene(stage, activeScene, scene);
    }

    public void goToMainMenu() {
        this.setScene(new MainMenuScene(this));
        setInMenu(true);
        terminateClient();
        terminateServer();
    }

    /**
     * Shows the lobby screen and starts a server.
     *
     * @param roomName       the name of the lobby
     * @param baseMultiplier the base multiplier of the game
     * @param customSentence a custom sentence for the game, can be null
     */
    public void createLobby(String roomName, float baseMultiplier, String customSentence) {
        LOG.info("Creating lobby named {}", roomName);

        this.baseMultiplier = baseMultiplier;
        this.customSentence = customSentence;
        this.team = 1;
        LOG.info("Setting team number to 1 (host default)");

        var lobby = new LobbyGameScene(this, roomName, true);
        lobby.addPlayer(
                TypeOfWar.getUserConfig().getName(), UserConfigManager.parseColor(TypeOfWar.getUserConfig()), 1);
        setScene(lobby);

        if (server != null) throw new IllegalStateException("Server already exists, cannot establish connection");

        server = new GameServer(this);
        isInMenu = true;
        isGameEndSignalled = false;
        try {
            server.start();
        } catch (IOException e) {
            LOG.error("Failed to start server", e);
        }
    }

    /**
     * Handles a game start
     *
     * @param sentence   the custom sentence, this is ignored server-side
     * @param multiplier the base multiplier, this is ignored server-side
     *
     * @throws IllegalStateException if there is no active server or client
     */
    public void startGame(String sentence, float multiplier) {
        List<PlayerObject> t1;
        List<PlayerObject> t2;

        if (server != null) {
            t1 = server.getTeam(1)
                       .map(p -> new PlayerObject(p.getName(), Color.color(p.getR(), p.getG(), p.getB())))
                       .toList();
            t2 = server.getTeam(2)
                       .map(p -> new PlayerObject(p.getName(), Color.color(p.getR(), p.getG(), p.getB())))
                       .toList();

            try {
                sentence = customSentence.isBlank()
                           ? WordGen.generateSentence(10_000)
                           : WordGen.loopSentence(customSentence, 3_000);
            } catch (IOException e) {
                LOG.fatal("Could not generate sentence", e);
            }
            multiplier = baseMultiplier;

            server.broadcast(new GamePacket(
                    GamePacket.Type.SRV_GAME_STARTING,
                    GameInitProto.GameData.newBuilder().setSentence(sentence).setMultiplier(multiplier).build()));
        } else if (client != null) {
            t1 = client.getTeam(1).stream().map(
                    p -> new PlayerObject(p.getName(), Color.color(p.getR(), p.getG(), p.getB()))).toList();
            t2 = client.getTeam(2).stream().map(
                    p -> new PlayerObject(p.getName(), Color.color(p.getR(), p.getG(), p.getB()))).toList();
        } else {
            throw new IllegalStateException("You cannot start the game without an active server or client!");
        }

        isGameEndSignalled = false;
        isInMenu = false;

        String finalSentence = sentence;
        float finalMultiplier = multiplier;
        Platform.runLater(() -> setScene(new TypeOfWarScene(this, t1, t2, finalSentence, finalMultiplier)));
    }

    /**
     * Handles a correct key press
     *
     * @return true if this is the server (and the rope should be moved)
     *
     * @throws IllegalStateException if there is no active server or client
     */
    public boolean sendCorrectKeyPress() {
        if (server != null) {
            // tell everyone a key was pressed
            server.broadcast(new GamePacket(GamePacket.Type.SRV_KEY_PRESS, new byte[]{ 1 }));
            return true;
        } else if (client != null) {
            // tell the server a key was pressed (the server will broadcast it to everyone else)
            client.sendServer(new GamePacket(GamePacket.Type.CLT_KEY_PRESS));
            return false;
        } else {
            throw new IllegalStateException("You cannot run onCorrectKeyPressed without an active server or client!");
        }
    }

    public void showJoinGameMenu() {
        discoverer = new ServerDiscoverer(this);
        discoverer.start();
        setScene(new JoinGameScene(this, this::joinGame));
    }

    /**
     * Joins a remote server, registers itself, and displays the lobby.
     *
     * @param serverAddress address and port of the server to join (addr:port)
     */
    public void joinGame(String serverAddress) {
        String host;
        int port;
        int i = serverAddress.lastIndexOf(':');
        if (i == -1) {
            host = serverAddress;
            port = GameServer.DEFAULT_PORT;
        } else {
            host = serverAddress.substring(0, i);
            port = Integer.parseInt(serverAddress.substring(i + 1));
        }

        discoverer.stop();

        LOG.info("Attempting to join game at {}, port {}", host, port);

        if (client != null) throw new IllegalStateException("Client already exists, cannot establish connection");

        setScene(new MenuQuestionScene(
                "JOINING SERVER...", "CHOOSE A TEAM TO JOIN.",
                "TEAM 1", () -> joinGameWithTeam(host, port, 1),
                "TEAM 2", () -> joinGameWithTeam(host, port, 2)));
    }

    private void joinGameWithTeam(String host, int port, int team) {
        this.team = team;
        setInMenu(false);
        LOG.info("Setting team number to {}", team);
        try {
            client = new GameClient(host, port, this, team);
            client.connect();
        } catch (ConnectException e) {
            terminateClient();
            showAlert("COULD NOT JOIN SERVER", e.getMessage());
        } catch (IOException e) {
            terminateClient();
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks the active {@link TypeOfWar.NetworkRole} and, if it is a SERVER, signals a game end to connected clients.
     */
    public void signalGameEnd(int winningTeam) {
        // only the sever can end the game ;)
        if (isGameEndSignalled || getNetworkRole() != TypeOfWar.NetworkRole.SERVER) return;

        LOG.info("Signalling game end now");
        this.winningTeam = winningTeam;

        // ask everyone for game stats
        server.broadcast(new GamePacket(GamePacket.Type.SRV_REQ_END_GAME_STATS));
        isGameEndSignalled = true;
    }

    /**
     * Immediately shows the end game screen without any additional checks
     */
    public void showEndGameScreen(EndGameProto.AllStats stats) {
        LOG.info("Showing end screen; winning team = {}", stats.getWinningTeam());

        List<EndResultEntity> t1 = stats.getStatsList()
                                        .stream()
                                        .filter(s -> s.getTeam() == 1)
                                        .map(NetUtils::endStatProtoToEntity)
                                        .toList();

        List<EndResultEntity> t2 = stats.getStatsList()
                                        .stream()
                                        .filter(s -> s.getTeam() == 2)
                                        .map(NetUtils::endStatProtoToEntity)
                                        .toList();

        if (getNetworkRole() == NetworkRole.SERVER) isInMenu = true;

        this.setScene(new EndGameScene(this, t1, t2, stats.getWinningTeam() == team));
    }

    /**
     * Closes the client and nullifies the pointer.
     */
    private void terminateClient() {
        if (client == null) {
            LOG.warn("Client does not exist, skipping termination");
            return;
        }

        try {
            client.close();
        } catch (IOException e) {
            LOG.error("Failed to close socket during termination", e);
        }
        client = null;
    }

    /**
     * Closes the terminator and nullifies the pointer.
     */
    private static void terminateDiscoverer() {
        if (discoverer == null) {
            LOG.warn("Server discoverer does not exist, skipping termination");
            return;
        }

        discoverer.stop();
        discoverer = null;
    }

    /**
     * Stops the server and nullifies the pointer.
     */
    private void terminateServer() {
        if (server == null) {
            LOG.warn("Server does not exist, skipping termination");
            return;
        }

        try {
            server.stop();
        } catch (IOException e) {
            LOG.error("Failed to close socket during termination", e);
        }
        server = null;
    }

    /**
     * Discards the current scene and shows a new {@link MenuAlertScene} with the provided alert details.
     */
    public void showAlert(String title, String message) {
        setScene(new MenuAlertScene(title, message, this));
    }

    /**
     * Tries to return the active scene as the (expected) type, casting it to said type, and returning null if such
     * fails.
     *
     * @param type the expected type of {@link GameScene}
     *
     * @return the active {@link GameScene} if it is the right type, or null if it's not
     */
    public <T extends GameScene> T getActiveScene(Class<T> type) {
        if (!type.isInstance(activeScene))
            return null;

        return type.cast(activeScene);
    }

    /**
     * Gets the current network role based on the active network manager (server or client)
     *
     * @return SERVER, CLIENT, or NONE
     */
    public NetworkRole getNetworkRole() {
        if (server != null) return NetworkRole.SERVER;
        else if (client != null) return NetworkRole.CLIENT;
        return NetworkRole.NONE;
    }

    /**
     * A network role is the "active" type of network manager
     */
    public enum NetworkRole {
        SERVER, CLIENT, NONE
    }

    public EndGameProto.PlayerStats getEndGameStats() {
        return endGameStats;
    }

    public void setEndGameStats(GameStatisticsEntity stats) {
        if (getNetworkRole() != NetworkRole.SERVER) return; // this is only for the sever

        Color color = UserConfigManager.parseColor(TypeOfWar.getUserConfig());

        this.endGameStats = EndGameProto.PlayerStats.newBuilder()
                                                    .setPlayerName(getUserConfig().getName())
                                                    .setTeam(team)
                                                    .setR((float)color.getRed())
                                                    .setG((float)color.getGreen())
                                                    .setB((float)color.getBlue())
                                                    .setWpm(stats.getWpm())
                                                    .setAccuracy(stats.getAccuracy())
                                                    .setWords(stats.getCorrectWords())
                                                    .build();
    }

    public int getTeam() {
        return team;
    }

    public int getWinningTeam() {
        return winningTeam;
    }

    /**
     * @return true if we are in a game, false if we are in the menu
     */
    public boolean isInGame() {
        return !isInMenu;
    }

    public void setInMenu(boolean inMenu) {
        isInMenu = inMenu;
    }

    /**
     * Gets the user config that is actively loaded in memory, NOT from disk.
     *
     * @return stored user confirm
     *
     * @throws NullPointerException if there is no stored user config (this shouldn't happen)
     */
    public static ConfigProto.UserConfig getUserConfig() {
        if (userConfig == null) throw new NullPointerException("User config is null!");
        return userConfig;
    }

    /**
     * Updates only the fields specified and saves the resulting config.
     *
     * @param partial the partial values, whatever is set here will be updated, otherwise it will remain the same.
     */
    public static void updateUserConfig(ConfigProto.UserConfig partial) {
        userConfig = UserConfigManager.update(userConfig, partial);
    }
}