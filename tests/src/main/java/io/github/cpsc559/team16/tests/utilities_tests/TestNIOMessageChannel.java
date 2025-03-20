package io.github.cpsc559.team16.tests.utilities_tests;

import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestNIOMessageChannel {

    /**
     *  Test: Send and receive a message over two connected `SocketChannel` instances.
     */
    public static void testSendAndReceiveMessage() {
        try {
            System.out.println(" Setting up test for send and receive...");

            //  Create a server socket channel that listens on a port
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(6000));
            serverSocket.configureBlocking(false);  //  Use non-blocking mode for real-world testing

            //  Create a client socket channel that connects to the server
            SocketChannel clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress("localhost", 6000));

            //  Accept the client connection on the server side
            SocketChannel serverChannel;
            while ((serverChannel = serverSocket.accept()) == null) {
                Thread.sleep(10);  // Wait for the connection
            }

            System.out.println(" Channels successfully connected!");

            //  Wrap them in `NIOMessageChannel`
            NIOMessageChannel clientMessageChannel = new NIOMessageChannel(clientChannel, Long.valueOf(105L));
            NIOMessageChannel serverMessageChannel = new NIOMessageChannel(serverChannel, Long.valueOf(105L));

            //  Define the test message
            String testMessage = "Hello, NIO!";
            System.out.println("Client sending message: " + testMessage);
            clientMessageChannel.sendMessage(testMessage);

            //  Read the message on the server side
            String receivedMessage;
            while ((receivedMessage = serverMessageChannel.receiveMessage()) == null) {
                Thread.sleep(10);  // Wait for message to be received
            }

            System.out.println("Server received message: " + receivedMessage);

            //  Ensure the received message matches the sent message
            if (testMessage.equals(receivedMessage)) {
                System.out.println("testSendAndReceiveMessage() PASSED");
            } else {
                System.out.println("testSendAndReceiveMessage() FAILED: Expected [" + testMessage + "] but received [" + receivedMessage + "]");
            }

            //  Cleanup
            clientChannel.close();
            serverChannel.close();
            serverSocket.close();
        } catch (IOException | InterruptedException e) {
            System.out.println("testSendAndReceiveMessage() FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void testSendAndReceiveMultipleMessages() {
        try {
            System.out.println("Setting up test for multiple messages...");

            // Create server socket and client connection
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(6001));
            serverSocket.configureBlocking(false);

            SocketChannel clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress("localhost", 6001));

            SocketChannel serverChannel;
            while ((serverChannel = serverSocket.accept()) == null) {
                Thread.sleep(10);
            }

            System.out.println("Channels successfully connected!");

            // Wrap them in `NIOMessageChannel`
            NIOMessageChannel clientMessageChannel = new NIOMessageChannel(clientChannel, Long.valueOf(105L));
            NIOMessageChannel serverMessageChannel = new NIOMessageChannel(serverChannel, Long.valueOf(105L));

            // Define test messages
            String[] messagesToSend = {"Hello", "How are you?", "Goodbye!"};

            // Send all messages from client to server
            for (String message : messagesToSend) {
                System.out.println("Client sending: " + message);
                clientMessageChannel.sendMessage(message);
            }

            // Receive all messages on the server side
            List<String> receivedMessages = new ArrayList<>();
            while (receivedMessages.size() < messagesToSend.length) {
                List<String> messages = serverMessageChannel.receiveAllMessages();
                if (messages != null) {
                    receivedMessages.addAll(messages);
                    for (String msg : messages) {
                        System.out.println("Server received: " + msg);
                    }
                }
                Thread.sleep(10);  // Avoid busy waiting
            }

            // Verify all messages are received correctly
            boolean testPassed = Arrays.equals(messagesToSend, receivedMessages.toArray());
            if (testPassed) {
                System.out.println("testSendAndReceiveMultipleMessages() PASSED");
            } else {
                System.out.println("testSendAndReceiveMultipleMessages() FAILED");
            }

            // Cleanup
            clientChannel.close();
            serverChannel.close();
            serverSocket.close();
        } catch (IOException | InterruptedException e) {
            System.out.println("testSendAndReceiveMultipleMessages() FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void testSendAndReceiveLargeMessage() {
        try {
            System.out.println(" Setting up test for large message...");

            // Create server socket and client connection
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(6002));
            serverSocket.configureBlocking(false);

            SocketChannel clientChannel = SocketChannel.open();
            clientChannel.connect(new InetSocketAddress("localhost", 6002));

            SocketChannel serverChannel;
            while ((serverChannel = serverSocket.accept()) == null) {
                Thread.sleep(10);  // Wait for connection
            }

            System.out.println("Channels successfully connected!");

            // Wrap them in `NIOMessageChannel`
            NIOMessageChannel clientMessageChannel = new NIOMessageChannel(clientChannel, Long.valueOf(105L));
            NIOMessageChannel serverMessageChannel = new NIOMessageChannel(serverChannel, Long.valueOf(105L));

            // Define a large test message that exceeds `streamBuffer` size (1024 bytes)
            String largeMessage = "A".repeat(5000) + "\n";  // A 5000-character message

            System.out.println("Client sending large message...");
            clientMessageChannel.sendMessage(largeMessage);

            // Receive the large message in multiple reads
            StringBuilder receivedData = new StringBuilder();
            String received;
            while ((received = serverMessageChannel.receiveMessage()) == null) {
                Thread.sleep(10);  // Wait for data
            }
            receivedData.append(received);

            System.out.println("Server received " + receivedData.length() + " bytes.");

            // Verify the received message matches the sent message
            if (receivedData.toString().equals(largeMessage.trim())) {
                System.out.println("testSendAndReceiveLargeMessage() PASSED");
            } else {
                System.out.println("testSendAndReceiveLargeMessage() FAILED: Data mismatch!");
            }

            // Cleanup
            clientChannel.close();
            serverChannel.close();
            serverSocket.close();
        } catch (IOException | InterruptedException e) {
            System.out.println("testSendAndReceiveLargeMessage() FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     *  Test: Handle IOException when sending a message.
     */
    public static void testHandleIOExceptionOnSend() {
        try {
            SocketChannel mockChannel = SocketChannel.open();
            NIOMessageChannel messageChannel = new NIOMessageChannel(mockChannel, Long.valueOf(105L));

            mockChannel.close(); // Force IOException

            messageChannel.sendMessage("This should fail.");
            System.out.println("testHandleIOExceptionOnSend() FAILED (Expected IOException)");
        } catch (IOException e) {
            System.out.println("testHandleIOExceptionOnSend() PASSED (Caught IOException)");
        }
    }

    /**
     *  Test: Handle IOException when receiving a message.
     */
    public static void testHandleIOExceptionOnReceive() {
        try {
            SocketChannel mockChannel = SocketChannel.open();
            NIOMessageChannel messageChannel = new NIOMessageChannel(mockChannel, Long.valueOf(105L));

            mockChannel.close(); // Force IOException

            messageChannel.receiveMessage();
            System.out.println("testHandleIOExceptionOnReceive() FAILED (Expected IOException)");
        } catch (IOException e) {
            System.out.println("testHandleIOExceptionOnReceive() PASSED (Caught IOException)");
        }
    }
}
