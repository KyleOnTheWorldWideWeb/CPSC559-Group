package io.github.cpsc559.team16.tests;

import io.github.cpsc559.team16.addressingserver.AddressingServer;
import io.github.cpsc559.team16.addressingserver.ServerInfo;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.tests.addressingservertests.TestAddressingServer;
import io.github.cpsc559.team16.tests.addressingservertests.TestServerInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TestManager {
    private static final TestManager INSTANCE = new TestManager();

    private TestManager() {
        System.out.println("TestManager initialized. Type a method name to run it.");
    }

    public static TestManager getInstance() {
        return INSTANCE;
    }


    public static void testMethod1() {
        System.out.println("Test Method 1 executed!");
        listMethods();
    }

    public static void testMethod2() {
        System.out.println("Test Method 2 executed!");
        listMethods();
    }

    public static void testMethod3() {
        System.out.println("Test Method 3 executed!");
        listMethods();
    }

    public static void addressingServerTest() {
        System.out.print("Addressing Server Test will fail if an Addressing Server container is not running.\n" +
                "Ensure you have the container running and call the method <serverIsRunning>");

    }

    public static void serverIsRunning() {
        TestAddressingServer.addressingTest();
        listMethods();
    }

    public static void serverInfoTest() {
        TestServerInfo.createServerRecord();
        listMethods();
    }

    public static void listMethods() {
        System.out.println("\nAvailable methods:");
        System.out.println(" - testMethod1");
        System.out.println(" - testMethod2");
        System.out.println(" - testMethod3");
        System.out.println(" - addressingServerTest");
        System.out.println(" - serverInfoTest");
        System.out.println(" - exit (to quit)\n");
    }
}

