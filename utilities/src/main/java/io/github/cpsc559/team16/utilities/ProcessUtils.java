package io.github.cpsc559.team16.utilities;

public class ProcessUtils {
    /**
     * Gets the current process ID (PID).
     * @return the PID of the current process.
     */
    public static long getPid() {
        return ProcessHandle.current().pid();
    }

    public static void main(String[] args) {
        System.out.println("Current PID: " + getPid());
    }
}
