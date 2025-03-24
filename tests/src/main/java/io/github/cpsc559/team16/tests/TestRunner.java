package io.github.cpsc559.team16.tests;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Scanner;


import java.util.concurrent.TimeUnit;

public class TestRunner {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {System.err.println(e.getMessage());}
        System.out.println("Enter a method name to run (or type 'list' to see all methods):");
        while (true) {
            System.out.print("Enter a method name to run (or type 'list' to see all methods):\n> ");
            String methodName = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(methodName)) {
                System.out.println("Exiting test runner...");
                break;
            }

            if ("list".equalsIgnoreCase(methodName)) {
                TestManager.listMethods();
                continue;
            }

            try {
                Method method = TestManager.class.getMethod(methodName);
                System.out.println("Running: " + methodName);
                method.invoke(null);
                System.out.println("Method executed successfully!");
            } catch (NoSuchMethodException e) {
                System.err.println("No such test method: " + methodName);
            } catch (InvocationTargetException e) {
                System.err.println("Error executing method: " + methodName);
                e.getCause().printStackTrace();  //  Print full error stack trace
            } catch (Exception e) {
                System.err.println("Unexpected error executing method: " + methodName);
                e.printStackTrace();
            }
        }
    }

}




