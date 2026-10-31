package com.moveinsync.config.sdk;

@FunctionalInterface
public interface TokenProvider {
    String token();

    static TokenProvider staticToken(String token) {
        return () -> token;
    }
}
