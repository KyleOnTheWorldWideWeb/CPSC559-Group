// package io.github.cpsc559.team16.tests.addressingservertests;

// import io.github.cpsc559.team16.addressingserver.ChatServerInfo;
// import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;

// public class TestServerInfo {

// public static void createServerRecord() {
// System.out.println(">----Starting Test for Server Info----<");

// // Create a ChatServerInfo instance with a maximum of 3 clients
// // ChatServerInfo serverInfo = new ChatServerInfo(1337L, "127.0.0.1", 3000,
// 4000, 3);
// System.out.println("Is chat server full? " + serverInfo.isFull());

// // Test addClient() until the server is full
// try {
// System.out.println("Adding client 1");
// serverInfo.addClient();
// System.out.println("Adding client 2");
// serverInfo.addClient();
// System.out.println("Adding client 3");
// serverInfo.addClient();
// System.out.println("Is server full? " + serverInfo.isFull());
// // This next addition should trigger a ChatServerFullException
// System.out.println("Attempting to add client 4 (should fail and trigger a
// ChatServerFullException)");
// serverInfo.addClient();
// } catch (ChatServerFullException e) {
// System.out.println("Expected exception caught: " + e.getMessage());
// }

// // Remove some clients and verify the server is no longer full
// System.out.println("Removing 2 clients");
// serverInfo.removeClients(2);
// System.out.println("Is chat server full? " + serverInfo.isFull());

// // Test status transitions: mark inactive then reactivate
// System.out.println("Marking server as inactive");
// serverInfo.markAsInactive();
// System.out.println("Status after marking inactive: " +
// serverInfo.getStatus());
// try {
// System.out.println("Reactivating server (client count should reset to 0)");
// serverInfo.markAsActive();
// System.out.println("Status after reactivation: " + serverInfo.getStatus());
// } catch (IllegalStateException e) {
// System.out.println("Unexpected error during reactivation: " +
// e.getMessage());
// }

// System.out.println(">----Server Info Test Complete----<");
// }

// }
