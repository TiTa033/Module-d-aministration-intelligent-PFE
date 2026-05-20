package talan.pfe.rulengine.entites;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleCondition.evaluate()
 * Tests all operators × all data types — pure logic, no Spring context.
 */
class RuleConditionEvaluateTest {

    private RuleCondition cond(Operator op, String value, DataType type) {
        return RuleCondition.builder()
                .field("x")
                .operator(op)
                .value(value)
                .valueType(type)
                .build();
    }

    // ─── NULL CHECKS ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IS_NULL / IS_NOT_NULL")
    class NullChecks {

        @Test void isNull_onNull_returnsTrue() {
            assertThat(cond(Operator.IS_NULL, "", DataType.STRING).evaluate(null)).isTrue();
        }

        @Test void isNull_onNonNull_returnsFalse() {
            assertThat(cond(Operator.IS_NULL, "", DataType.STRING).evaluate("value")).isFalse();
        }

        @Test void isNotNull_onNull_returnsFalse() {
            assertThat(cond(Operator.IS_NOT_NULL, "", DataType.STRING).evaluate(null)).isFalse();
        }

        @Test void isNotNull_onNonNull_returnsTrue() {
            assertThat(cond(Operator.IS_NOT_NULL, "", DataType.STRING).evaluate("value")).isTrue();
        }
    }

    // ─── STRING OPERATORS ───────────────────────────────────────────────────

    @Nested
    @DisplayName("STRING operators")
    class StringOperators {

        @Test void equals_match() {
            assertThat(cond(Operator.EQUALS, "APPROVED", DataType.STRING).evaluate("APPROVED")).isTrue();
        }

        @Test void equals_noMatch() {
            assertThat(cond(Operator.EQUALS, "APPROVED", DataType.STRING).evaluate("REJECTED")).isFalse();
        }

        @Test void notEquals_match() {
            assertThat(cond(Operator.NOT_EQUALS, "APPROVED", DataType.STRING).evaluate("REJECTED")).isTrue();
        }

        @Test void contains_match() {
            assertThat(cond(Operator.CONTAINS, "VIP", DataType.STRING).evaluate("VIP_CUSTOMER")).isTrue();
        }

        @Test void contains_noMatch() {
            assertThat(cond(Operator.CONTAINS, "VIP", DataType.STRING).evaluate("STANDARD")).isFalse();
        }

        @Test void notContains_match() {
            assertThat(cond(Operator.NOT_CONTAINS, "VIP", DataType.STRING).evaluate("STANDARD")).isTrue();
        }

        @Test void startsWith_match() {
            assertThat(cond(Operator.STARTS_WITH, "TN", DataType.STRING).evaluate("TN0012345")).isTrue();
        }

        @Test void startsWith_noMatch() {
            assertThat(cond(Operator.STARTS_WITH, "TN", DataType.STRING).evaluate("FR0012345")).isFalse();
        }

        @Test void endsWith_match() {
            assertThat(cond(Operator.ENDS_WITH, ".com", DataType.STRING).evaluate("user@bank.com")).isTrue();
        }

        @Test void inList_match() {
            assertThat(cond(Operator.IN_LIST, "TN,FR,DE", DataType.STRING).evaluate("FR")).isTrue();
        }

        @Test void inList_noMatch() {
            assertThat(cond(Operator.IN_LIST, "TN,FR,DE", DataType.STRING).evaluate("US")).isFalse();
        }

        @Test void notInList_match() {
            assertThat(cond(Operator.NOT_IN_LIST, "TN,FR,DE", DataType.STRING).evaluate("US")).isTrue();
        }

        @Test void nullInput_returnsFalse() {
            assertThat(cond(Operator.EQUALS, "APPROVED", DataType.STRING).evaluate(null)).isFalse();
        }
    }

    // ─── NUMBER OPERATORS ───────────────────────────────────────────────────

    @Nested
    @DisplayName("NUMBER operators")
    class NumberOperators {

        @Test void greaterThan_match() {
            assertThat(cond(Operator.GREATER_THAN, "1000", DataType.NUMBER).evaluate(1500)).isTrue();
        }

        @Test void greaterThan_noMatch() {
            assertThat(cond(Operator.GREATER_THAN, "1000", DataType.NUMBER).evaluate(500)).isFalse();
        }

        @Test void lessThan_match() {
            assertThat(cond(Operator.LESS_THAN, "700", DataType.NUMBER).evaluate(500)).isTrue();
        }

        @Test void greaterOrEqual_boundary() {
            assertThat(cond(Operator.GREATER_OR_EQUAL, "1000", DataType.NUMBER).evaluate(1000)).isTrue();
        }

        @Test void lessOrEqual_boundary() {
            assertThat(cond(Operator.LESS_OR_EQUAL, "1000", DataType.NUMBER).evaluate(1000)).isTrue();
        }

        @Test void equals_number() {
            assertThat(cond(Operator.EQUALS, "42", DataType.NUMBER).evaluate(42)).isTrue();
        }

        @Test void notEquals_number() {
            assertThat(cond(Operator.NOT_EQUALS, "42", DataType.NUMBER).evaluate(43)).isTrue();
        }


    }

    // ─── BOOLEAN OPERATORS ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BOOLEAN operators")
    class BooleanOperators {

        @Test void equals_true() {
            assertThat(cond(Operator.EQUALS, "true", DataType.BOOLEAN).evaluate("true")).isTrue();
        }

        @Test void equals_false_noMatch() {
            assertThat(cond(Operator.EQUALS, "true", DataType.BOOLEAN).evaluate("false")).isFalse();
        }

        @Test void notEquals_boolean() {
            assertThat(cond(Operator.NOT_EQUALS, "true", DataType.BOOLEAN).evaluate("false")).isTrue();
        }

        @Test void unsupportedOperator_returnsFalse() {
            // GREATER_THAN is not applicable to BOOLEAN — should return false
            assertThat(cond(Operator.GREATER_THAN, "true", DataType.BOOLEAN).evaluate("true")).isFalse();
        }
    }

    // ─── DATE OPERATORS ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("DATE operators")
    class DateOperators {

        @Test void equals_sameDate() {
            assertThat(cond(Operator.EQUALS, "2025-01-01", DataType.DATE).evaluate("2025-01-01")).isTrue();
        }

        @Test void greaterThan_afterDate() {
            assertThat(cond(Operator.GREATER_THAN, "2024-01-01", DataType.DATE).evaluate("2025-01-01")).isTrue();
        }

        @Test void lessThan_beforeDate() {
            assertThat(cond(Operator.LESS_THAN, "2026-01-01", DataType.DATE).evaluate("2025-01-01")).isTrue();
        }

        @Test void greaterOrEqual_sameDate() {
            assertThat(cond(Operator.GREATER_OR_EQUAL, "2025-01-01", DataType.DATE).evaluate("2025-01-01")).isTrue();
        }

        @Test void lessOrEqual_sameDate() {
            assertThat(cond(Operator.LESS_OR_EQUAL, "2025-01-01", DataType.DATE).evaluate("2025-01-01")).isTrue();
        }

        @Test void notEquals_differentDate() {
            assertThat(cond(Operator.NOT_EQUALS, "2025-01-01", DataType.DATE).evaluate("2024-12-31")).isTrue();
        }
    }
}
