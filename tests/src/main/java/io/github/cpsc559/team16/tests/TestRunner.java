package io.github.cpsc559.team16.tests;
import java.lang.reflect.Method;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import io.github.cpsc559.team16.common.exceptions.ChatServerFullException;
import io.github.cpsc559.team16.addressingserver.ServerInfo;
import io.github.cpsc559.team16.addressingserver.AddressingServer;


import java.lang.reflect.Method;
import java.util.Scanner;

public class TestRunner {
    public static void main(String[] args) {
        TestManager.getInstance(); // Ensure the singleton is initialized

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter a method name to run (or type 'list' to see all methods):");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Exiting test runner.");
                break;
            } else if (input.equalsIgnoreCase("list")) {
                TestManager.listMethods();
                continue;
            }

            try {
                Method method = TestManager.class.getMethod(input);
                method.invoke(null); // Invoke the static method
            } catch (NoSuchMethodException e) {
                System.out.println("Error: Method not found.");
            } catch (Exception e) {
                System.out.println("Error executing method: " + e.getMessage());
            }
        }

        scanner.close();
    }
}




