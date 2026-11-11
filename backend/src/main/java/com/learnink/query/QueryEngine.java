package com.learnink.query;

public enum QueryEngine {
    SQL("SQL (MySQL)"),
    MONGODB("NoSQL (MongoDB)");

    private final String label;

    QueryEngine(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
