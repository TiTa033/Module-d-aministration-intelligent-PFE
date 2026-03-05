package talan.pfe.rulengine.enums;

public enum Operator {
    // Equality
    EQUALS,
    NOT_EQUALS,

    // Numeric comparison
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,

    // String operations
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,

    // List operations
    IN_LIST,
    NOT_IN_LIST,

    // Null checks
    IS_NULL,
    IS_NOT_NULL
}