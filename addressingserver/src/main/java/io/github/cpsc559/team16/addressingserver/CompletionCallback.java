package io.github.cpsc559.team16.addressingserver;

import java.io.IOException;

@FunctionalInterface
public interface CompletionCallback {
    void run() throws IOException;
}
