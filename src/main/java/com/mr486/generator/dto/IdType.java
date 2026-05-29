package com.mr486.generator.dto;

public enum IdType {
    INTEGER,
    LONG,
    UUID,
    /**
     * Accepted for request compatibility only.
     * Mongo resources are generated with String identifiers automatically.
     */
    STRING
}
