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
import java.util.concurrent.TimeUnit;

public class TestRunner {
    public static void main(String[] args) {
        TestManager.getInstance();

        Scanner scanner = new Scanner(System.in);
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {System.err.println(e.getMessage());}
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
                System.out.printf("Error: Method < %s > not found.%n", input);
            } catch (Exception e) {
                System.out.println("Error executing method: " + e.getMessage());
            }
        }

        scanner.close();
    }
}




