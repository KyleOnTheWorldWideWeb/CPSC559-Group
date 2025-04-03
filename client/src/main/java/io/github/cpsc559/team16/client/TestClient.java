package io.github.cpsc559.team16.client;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.cpsc559.team16.common.messaging.AckMessage;
import io.github.cpsc559.team16.common.messaging.AckObjectTypes;
import io.github.cpsc559.team16.common.messaging.RegisterMessage;
import io.github.cpsc559.team16.common.utilities.BaseMessage;
import io.github.cpsc559.team16.common.utilities.ClientServerMessage;

public class TestClient extends Client {

    private static final String[] RANDOM_MESSAGES = {
        "Lorem ipsum dolor sit amet.",
        "Consectetur adipiscing elit.",
        "Sed do eiusmod tempor incididunt ut labore.",
        "Ut enim ad minim veniam.",
        "Quis nostrud exercitation ullamco laboris nisi ut aliquip.",
        "Ex ea commodo consequat.",
        "Duis aute irure dolor in reprehenderit.",
        "In voluptate velit esse cillum dolore eu fugiat nulla pariatur.",
        "Excepteur sint occaecat cupidatat non proident.",
        "Sunt in culpa qui officia deserunt mollit anim id est laborum."
    };

    private final ScheduledExecutorService messageScheduler = Executors.newScheduledThreadPool(1);

    public TestClient(String username, String serverName, int serverPort) {
        super(username, serverName, serverPort, null, null);
    }

    @Override
    public void run() {
        debug(DEBUG_BASIC, "Starting TestClient...");
        terminate = false;

        try {
            connect();

            // Schedule random message sending every 5 seconds
            messageScheduler.scheduleAtFixedRate(() -> {
                if (!terminate && isConnected) {
                    String randomMessage = getRandomMessage();
                    ClientServerMessage message = createMessage(randomMessage, "fellow clients");
                    sendMessage(message);
                    debug(DEBUG_BASIC, "Sent random message: " + randomMessage);
                }
            }, 0, 5, TimeUnit.SECONDS);

            // Keep the client running until terminated
            while (!terminate) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            debug(DEBUG_BASIC, "Error in TestClient run: " + e.getMessage());
            shutdown();
        } finally {
            shutdown();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        messageScheduler.shutdown();
        debug(DEBUG_BASIC, "TestClient shutdown complete.");
    }

    private String getRandomMessage() {
        Random random = new Random();
        return RANDOM_MESSAGES[random.nextInt(RANDOM_MESSAGES.length)];
    }
}