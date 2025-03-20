package io.github.cpsc559.team16.tests;

import io.github.cpsc559.team16.tests.addressingserver_tests.TestAddressingServer;
import io.github.cpsc559.team16.tests.addressingserver_tests.TestServerInfo;
import io.github.cpsc559.team16.tests.utilities_tests.TestNIOMessageChannel;

public class TestManager {
    private static final TestManager INSTANCE = new TestManager();

    private TestManager() {
        System.out.println("TestManager initialized. Type a method name to run it.");
    }

    public static TestManager getInstance() {
        return INSTANCE;
    }



    public static void t1() {
        System.out.print("Addressing Server Test will fail if an Addressing Server container is not running.\n" +
                "Ensure you have the container running and call the method <serverIsRunning>");

    }

    public static void t2() {
        TestAddressingServer.addressingTest();
        listMethods();
    }

    public static void t3() {
        TestServerInfo.createServerRecord();
        listMethods();
    }

    public static void t4() {
        TestNIOMessageChannel.testSendAndReceiveMessage();
        listMethods();
    }

    public static void t5() {
        TestNIOMessageChannel.testHandleIOExceptionOnSend();
        listMethods();
    }

    public static void t6() {
        TestNIOMessageChannel.testHandleIOExceptionOnReceive();
        listMethods();
    }

    public static void t7() {
        TestNIOMessageChannel.testSendAndReceiveLargeMessage();
        listMethods();
    }

    public static void t8() {
        TestNIOMessageChannel.testSendAndReceiveMultipleMessages();
        listMethods();
    }



    //
//    public static void testReceiveCompleteMessage() {
//        TestNIOMessageChannel.testReceiveCompleteMessage();
//        listMethods();
//    }
//
//    public static void testReceivePartialMessage() {
//        TestNIOMessageChannel.testReceivePartialMessage();
//        listMethods();
//    }
//
//    public static void testReceiveMultipleMessages() {
//        TestNIOMessageChannel.testReceiveMultipleMessages();
//        listMethods();
//    }

    public static void listMethods() {
        System.out.println("\nAvailable methods (enter ti into the console, where i > 0):");
        System.out.println(" - t1 (addressingServerTest)");
        System.out.println(" - t2 (serverIsRunning)");
        System.out.println(" - t3 (serverInfoTest)");
        System.out.println(" - t4 (testSendAndReceiveMessage)");
        System.out.println(" - t5 (testHandleIOExceptionOnSend)");
        System.out.println(" - t6 (testHandleIOExceptionOnReceive)");
        System.out.println(" - t7 (testSendAndReceiveLargeMessage)");
        System.out.println(" - t8 (testSendAndReceiveMultipleMessages)");
        System.out.println(" - exit (to quit)\n");
    }
}

