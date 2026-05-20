package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import talan.pfe.rulengine.entites.RuleAction;
import talan.pfe.rulengine.enums.ActionType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RuleAction.execute() — pure logic, no Spring context.
 * Tests that each ActionType produces the correct output value and type.
 */
@DisplayName("RuleAction.execute()")
class RuleActionExecuteTest {

    private RuleAction action(ActionType type, String value) {
        return RuleAction.builder()
                .actionType(type).outputKey("result").outputValue(value).build();
    }

    @Test @DisplayName("SET_VALUE should return raw string value")
    void setValue_returnsString() {
        assertThat(action(ActionType.SET_VALUE, "APPROVED").execute()).isEqualTo("APPROVED");
    }

    @Test @DisplayName("SET_FLAG true should return Boolean true")
    void setFlag_true_returnsTrue() {
        assertThat(action(ActionType.SET_FLAG, "true").execute()).isEqualTo(Boolean.TRUE);
    }

    @Test @DisplayName("SET_FLAG false should return Boolean false")
    void setFlag_false_returnsFalse() {
        assertThat(action(ActionType.SET_FLAG, "false").execute()).isEqualTo(Boolean.FALSE);
    }

    @Test @DisplayName("SET_SCORE should return Double value")
    void setScore_returnsDouble() {
        assertThat(action(ActionType.SET_SCORE, "85.5").execute()).isEqualTo(85.5);
    }

    @Test @DisplayName("REJECT should return string REJECTED")
    void reject_returnsRejected() {
        assertThat(action(ActionType.REJECT, "any").execute()).isEqualTo("REJECTED");
    }

    @Test @DisplayName("APPROVE should return string APPROVED")
    void approve_returnsApproved() {
        assertThat(action(ActionType.APPROVE, "any").execute()).isEqualTo("APPROVED");
    }

    @Test @DisplayName("REQUIRE_REVIEW should return string REVIEW")
    void requireReview_returnsReview() {
        assertThat(action(ActionType.REQUIRE_REVIEW, "any").execute()).isEqualTo("REVIEW");
    }
}
