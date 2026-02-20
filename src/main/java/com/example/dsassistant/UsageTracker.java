package com.example.dsassistant;

public class UsageTracker {
    private static int totalRequests = 0;
    private static int totalTokens = 0;

    public static void incrementRequests() {
        totalRequests++;
    }

    public static void addTokens(int tokens) {
        totalTokens += tokens;
    }

    public static int getTotalRequests() {
        return totalRequests;
    }

    public static int getTotalTokens() {
        return totalTokens;
    }

    public static void reset() {
        totalRequests = 0;
        totalTokens = 0;
    }
}