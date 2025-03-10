import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
 * Simulates the chat application, so far just for UI testing purposes.
 */
public class ChatSimulator {

    /* Where the chat log is stored */
    private static final LinkedList<Message> messageLog = new LinkedList<>();

    /* Where unsent messages are stored */
    private static final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private static final Queue<Message> awaitingAckQueue = new ConcurrentLinkedQueue<>();

    /* Server communication queues - these represent the socket */
    private static final Queue<Message> serverInbox = new ConcurrentLinkedQueue<>();
    private static final Queue<Message> serverOutbox = new ConcurrentLinkedQueue<>();

    private static final String username = "Jawash";
    private static volatile String currentInput = "";

    public static void main(String[] args) {
        new InputThread().start();
        new OutputThread().start();
        new ServerReceiveThread().start();
        new ServerSendThread().start();
        new ConnectionThread().start();
    }

    /** Represents a chat message */
    private static class Message {
        private static int idCounter = 0;

        private final String sender;
        private final String message;
        private final int id;

        public Message(String sender, String message) {
            this.sender = sender;
            this.message = message;
            this.id = idCounter++;
        }

        public String getSender() {
            return sender;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return sender + ": " + message;
        }
    }

    /** Simulates the ConnectionThread, handling message sending and receiving */
    private static class ConnectionThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {

                    // Check for messages from the server
                    while (!serverOutbox.isEmpty()) {
                        Message receivedMessage = serverOutbox.poll();
                        if (receivedMessage != null) {
                            messageLog.add(receivedMessage);

                            // If this is our own message, remove it from messageQueue
                            if (receivedMessage.getSender().equals(username)) {
                                awaitingAckQueue.removeIf(msg -> msg.getId() == receivedMessage.getId());
                            }
                        }
                    }

                    // Send new messages to the server
                    while (!messageQueue.isEmpty()) {

                        Message message = messageQueue.poll();
                        awaitingAckQueue.add(message);

                        if (message != null) {
                            serverInbox.add(message);
                        }
                    }

                } catch (Exception e) {
                    return;
                }
            }
        }
    }

    /** Handles user input and adds messages to messageQueue */
    private static class InputThread extends Thread {
        @Override
        public void run() {
            try {
                StringBuilder inputBuffer = new StringBuilder();

                while (true) {
                    char c = (char) System.in.read();

                    switch (c) {
                        case '\n' -> {
                            if (inputBuffer.length() > 0) {
                                String contents = inputBuffer.toString();
                                messageQueue.add(new Message(username, contents));
                                inputBuffer.setLength(0);
                            }
                        }
                        case '\b', 127 -> {
                            if (inputBuffer.length() > 0) {
                                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                            }
                        }
                        default -> inputBuffer.append(c);
                    }

                    // Update the current input for OutputThread
                    currentInput = inputBuffer.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** Periodically refreshes the UI while preserving user input */
    private static class OutputThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                    repaintUI();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private void repaintUI() {
            synchronized (System.out) {
                // Capture current user input
                String savedInput = currentInput;

                // Clear the console
                System.out.print("\033[H\033[2J"); // ANSI escape code for clearing screen
                System.out.flush();

                // Print chat log
                for (Message message : messageLog) {
                    System.out.println(message);
                }

                // Print unsent messages
                for (Message message : awaitingAckQueue) {
                    System.out.println(message + " [sending...]");
                }

                // Print unsent messages
                for (Message message : messageQueue) {
                    System.out.println(message + " [sending...]");
                }

                // Restore user input correctly (BUG: this doesn't work right now)
                System.out.print("> " + savedInput);
                System.out.flush();
            }
        }
    }

    /** Simulates the server receiving messages and echoing them back with a random delay */
    private static class ServerReceiveThread extends Thread {
        private final Random random = new Random();

        @Override
        public void run() {
            while (true) {
                if (!serverInbox.isEmpty()) {
                    Message receivedMessage = serverInbox.poll();
                    if (receivedMessage != null) {
                        try {
                            int delay = random.nextInt(3001); // Random delay between 0-3 seconds
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            return;
                        }
                        serverOutbox.add(receivedMessage);
                    }
                }
            }
        }
    }

    /** Simulates the server sending periodic (preset) messages */
    private static class ServerSendThread extends Thread {
        private final String[] presetNames = {
            "Aidan", "Chloe", "Kyle", "Parmeet", "Cole"
        };

        /** Thanks ChatGPT for generating these, I totally didn't spend 30 minutes writing all these messages myself */
        private final String[] presetMessages = {
            "Professor Jawash, please notice me!", 
            "I’d write an essay on how amazing Jawash is, but Java won’t compile it.",
            "Jawash, are you secretly a wizard? Because you’ve cast a spell on me.",
            "Java's syntax is hard, but your teaching is magic, Jawash!",
            "Just finished studying your lectures... still not worthy of your greatness, though.",
            "Dear Jawash, your knowledge is like the infinite loop of awesomeness.",
            "What’s the secret to your brilliance, Professor Jawash? Please, I’m begging!",
            "Do you give out autographs, or is that too much of a Java exception?",
            "Professor Jawash, I’m coding in your honor today. Please acknowledge me.",
            "I’m not saying you’re a legend, but my code compiles faster when I think of you.",
            "Can we get a Jawash fan club going? Just saying, I’d be first in line.",
            "If there was a course for being as cool as Jawash, I'd enroll 10 times.",
            "Jawash, your lectures are more powerful than a Java garbage collector!",
            "Just ran my code with the hope that Jawash will notice me. Fingers crossed!",
            "Is there a Java class for 'adoring Professor Jawash'? If not, there should be.",
            "Professor Jawash, you're the one true constant in this chaotic world of Java!",
            "Every time I solve a bug, I imagine you giving me an approving nod, Jawash.",
            "Do you think we can get a 'Jawash Appreciation Day' on the syllabus? Pretty please?",
            "Professor Jawash, you’re the main method in my life.",
            "Are you a compiler? Because I can’t seem to function without you.",
            "If I were an array, I’d be out of bounds without you, Jawash.",
            "Jawash, you must be a constructor—because you’re creating a whole new world for me.",
            "Are you a ‘public’ method? Because everyone needs access to you.",
            "If teaching was a sport, you'd be on the podium, Jawash.",
            "Java syntax is hard, but your brilliance? That’s easy.",
            "I swear your lectures are like magic. Where’s the wand, Jawash?",
            "Professor Jawash, you’re the exception to every rule.",
            "If I had a dollar for every time you taught me something new, I could retire by 25.",
            "Do you use recursion, Jawash? Because I can’t stop thinking about you.",
            "If you were a method, you’d definitely be ‘void awesome.’",
            "Every time I write ‘System.out.println(“Jawash is great!”)’ my code compiles perfectly.",
            "Professor, you’ve got more layers than a nested loop, and I’m here for it.",
            "Jawash, I’ve got a null pointer exception... for not talking to you more.",
            "Is there a class for ‘being too cool’? Because you’d teach it, Jawash.",
            "Is your name ‘static’? Because you’re the constant I need in my life.",
            "I’m coding in your honor, Jawash. Please acknowledge me before I get an exception.",
            "You must be a variable, because my heart is always referencing you.",
            "Jawash, you make every class feel like a ‘for loop’ of greatness.",
            "I’ve got a bug in my code. It’s called ‘not getting enough of your lectures.’",
            "Are you an interface? Because I can’t even implement my thoughts without you.",
            "You teach Java like you’ve already debugged the whole universe, Jawash.",
            "Professor Jawash, you’re the return value I’ve been looking for.",
            "Can we get ‘Jawash Appreciation Day’ as a class holiday? Please?",
            "You’re like the Java documentation I always needed—clear, concise, and full of wisdom.",
            "Can I import your wisdom into my life? I’m ready to start the class.",
            "I swear you’ve got the best algorithm for teaching, Jawash. I’m hooked.",
            "If I were a String, I’d be ‘Immutable,’ because I’m stuck on you, Jawash."
        };

        private final Random random = new Random();

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(4000); // Every 10 seconds send a random message
                    String sender = presetNames[random.nextInt(presetNames.length)];
                    String messageText = presetMessages[random.nextInt(presetMessages.length)];
                    serverOutbox.add(new Message(sender, messageText));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}