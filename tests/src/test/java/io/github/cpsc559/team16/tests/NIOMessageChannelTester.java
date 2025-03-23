package io.github.cpsc559.team16.tests;

import io.github.cpsc559.team16.common.utilities.NIOMessageChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NIOMessageChannelTester {
//    private SocketChannel mockChannel;
//    private NIOMessageChannel messageChannel;
//
//    @BeforeEach
//    void setUp() {
//        mockChannel = mock(SocketChannel.class);
//        messageChannel = new NIOMessageChannel(mockChannel);
//    }
//
//    @AfterEach
//    void tearDown() throws IOException {
//        messageChannel.close();
//    }
//
//    // =====================================
//    // EQUIVALENCE PARTITIONING TESTS
//    // =====================================
//
//    @Test
//    @DisplayName("Send a valid small message")
//    void testSendValidMessage() throws IOException {
//        String testMessage = "Hello, Replica!";
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(testMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    @Test
//    @DisplayName("Send an empty message (boundary case)")
//    void testSendEmptyMessage() throws IOException {
//        String testMessage = "";
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(testMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    @Test
//    @DisplayName("Send a long message (boundary case)")
//    void testSendLongMessage() throws IOException {
//        String longMessage = "A".repeat(65536);  // 64KB message
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(longMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    @Test
//    @DisplayName("Send null message should throw exception")
//    void testSendNullMessage() {
//        assertThrows(NullPointerException.class, () -> messageChannel.sendMessage(null));
//    }
//
//    // =====================================
//    // BOUNDARY VALUE TESTING
//    // =====================================
//
//    @Test
//    @DisplayName("Send minimum valid message")
//    void testSendMinimumValidMessage() throws IOException {
//        String testMessage = "A";  // Single character
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(testMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    @Test
//    @DisplayName("Send maximum message just under buffer limit")
//    void testSendMaxUnderBufferLimit() throws IOException {
//        String largeMessage = "B".repeat(16383);  // Just under 16KB buffer size
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(largeMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    @Test
//    @DisplayName("Send message exactly at buffer limit")
//    void testSendExactBufferLimitMessage() throws IOException {
//        String exactLimitMessage = "C".repeat(16384);  // Exactly 16KB
//        doNothing().when(mockChannel).write(any(ByteBuffer.class));
//
//        messageChannel.sendMessage(exactLimitMessage);
//
//        verify(mockChannel, atLeastOnce()).write(any(ByteBuffer.class));
//    }
//
//    // =====================================
//    // ERROR HANDLING & EXCEPTION TESTS
//    // =====================================
//
//    @Test
//    @DisplayName("Handle IOException when sending")
//    void testSendIOException() throws IOException {
//        doThrow(new IOException("Mock I/O error")).when(mockChannel).write(any(ByteBuffer.class));
//
//        assertThrows(IOException.class, () -> messageChannel.sendMessage("Test message"));
//    }
//
//    @Test
//    @DisplayName("Receive valid message")
//    void testReceiveValidMessage() throws IOException {
//        String testMessage = "Hello, World!\n";
//        ByteBuffer buffer = ByteBuffer.wrap(testMessage.getBytes(StandardCharsets.UTF_8));
//        when(mockChannel.read(any(ByteBuffer.class))).thenAnswer(invocation -> {
//            ByteBuffer argBuffer = invocation.getArgument(0);
//            argBuffer.put(buffer);
//            return testMessage.length();
//        });
//
//        String receivedMessage = messageChannel.receiveMessage();
//        assertEquals("Hello, World!", receivedMessage);
//    }
//
//    @Test
//    @DisplayName("Receive partial message and wait for completion")
//    void testReceivePartialMessage() throws IOException {
//        String partialMessage = "Hello, ";
//        String fullMessage = "World!\n";
//        ByteBuffer partialBuffer = ByteBuffer.wrap(partialMessage.getBytes(StandardCharsets.UTF_8));
//        ByteBuffer fullBuffer = ByteBuffer.wrap(fullMessage.getBytes(StandardCharsets.UTF_8));
//
//        when(mockChannel.read(any(ByteBuffer.class)))
//                .thenAnswer(invocation -> {
//                    ByteBuffer argBuffer = invocation.getArgument(0);
//                    argBuffer.put(partialBuffer);
//                    return partialMessage.length();
//                })
//                .thenAnswer(invocation -> {
//                    ByteBuffer argBuffer = invocation.getArgument(0);
//                    argBuffer.put(fullBuffer);
//                    return fullMessage.length();
//                });
//
//        String firstAttempt = messageChannel.receiveMessage();
//        assertNull(firstAttempt, "Partial messages should not be returned immediately.");
//
//        String completeMessage = messageChannel.receiveMessage();
//        assertEquals("Hello, World!", completeMessage);
//    }
//
//    @Test
//    @DisplayName("Handle IOException while receiving")
//    void testReceiveIOException() throws IOException {
//        when(mockChannel.read(any(ByteBuffer.class))).thenThrow(new IOException("Mock read error"));
//
//        assertThrows(IOException.class, () -> messageChannel.receiveMessage());
//    }
//
//    @Test
//    @DisplayName("Receive empty message should return null")
//    void testReceiveEmptyMessage() throws IOException {
//        ByteBuffer emptyBuffer = ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8));
//        when(mockChannel.read(any(ByteBuffer.class))).thenReturn(0);
//
//        String message = messageChannel.receiveMessage();
//        assertNull(message, "Empty reads should return null.");
//    }
//
//    @Test
//    @DisplayName("Closing channel prevents further reads/writes")
//    void testCloseChannel() throws IOException {
//        doNothing().when(mockChannel).close();
//
//        messageChannel.close();
//        verify(mockChannel, times(1)).close();
//
//        assertThrows(IOException.class, () -> messageChannel.sendMessage("Test after close"));
//        assertThrows(IOException.class, () -> messageChannel.receiveMessage());
//    }
}

