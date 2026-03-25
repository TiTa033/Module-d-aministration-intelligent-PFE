package talan.pfe.rulengine.entites;

import jakarta.persistence.*;
import lombok.*;
import talan.pfe.rulengine.enums.DataType;
import talan.pfe.rulengine.enums.Operator;
@Entity
@Table(name = "rule_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "field", nullable = false)
    private String field;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private Operator operator;

    @Column(name = "value", nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private DataType valueType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;




    public boolean evaluate(Object inputValue) {
        if (operator == Operator.IS_NULL) return inputValue == null;
        if (operator == Operator.IS_NOT_NULL) return inputValue != null;
        if (inputValue == null) return false;

        return switch (valueType) {
            case STRING   -> evaluateString(inputValue.toString());
            case NUMBER   -> evaluateNumber(inputValue.toString());
            case BOOLEAN  -> evaluateBoolean(inputValue.toString());
            case DATE     -> evaluateDate(inputValue.toString());
        };
    }

    private boolean evaluateString(String input) {
        return switch (operator) {
            case EQUALS       -> input.equals(value);
            case NOT_EQUALS   -> !input.equals(value);
            case CONTAINS     -> input.contains(value);
            case NOT_CONTAINS -> !input.contains(value);
            case STARTS_WITH  -> input.startsWith(value);
            case ENDS_WITH    -> input.endsWith(value);
            case IN_LIST      -> java.util.Arrays.asList(value.split(",")).contains(input);
            case NOT_IN_LIST  -> !java.util.Arrays.asList(value.split(",")).contains(input);
            default           -> false;
        };
    }

    private boolean evaluateNumber(String input) {
        double inputNum = Double.parseDouble(input);
        double condNum  = Double.parseDouble(value);
        return switch (operator) {
            case EQUALS          -> inputNum == condNum;
            case NOT_EQUALS      -> inputNum != condNum;
            case GREATER_THAN    -> inputNum > condNum;
            case LESS_THAN       -> inputNum < condNum;
            case GREATER_OR_EQUAL -> inputNum >= condNum;
            case LESS_OR_EQUAL   -> inputNum <= condNum;
            case IN_LIST         -> java.util.Arrays.stream(value.split(","))
                    .mapToDouble(Double::parseDouble)
                    .anyMatch(v -> v == inputNum);
            case NOT_IN_LIST     -> java.util.Arrays.stream(value.split(","))
                    .mapToDouble(Double::parseDouble)
                    .noneMatch(v -> v == inputNum);
            default              -> false;
        };
    }

    private boolean evaluateBoolean(String input) {
        boolean inputBool = Boolean.parseBoolean(input);
        boolean condBool  = Boolean.parseBoolean(value);
        return switch (operator) {
            case EQUALS     -> inputBool == condBool;
            case NOT_EQUALS -> inputBool != condBool;
            default         -> false;
        };
    }

    private boolean evaluateDate(String input) {
        java.time.LocalDate inputDate = java.time.LocalDate.parse(input);
        java.time.LocalDate condDate  = java.time.LocalDate.parse(value);
        return switch (operator) {
            case EQUALS          -> inputDate.isEqual(condDate);
            case NOT_EQUALS      -> !inputDate.isEqual(condDate);
            case GREATER_THAN    -> inputDate.isAfter(condDate);
            case LESS_THAN       -> inputDate.isBefore(condDate);
            case GREATER_OR_EQUAL -> !inputDate.isBefore(condDate);
            case LESS_OR_EQUAL   -> !inputDate.isAfter(condDate);
            default              -> false;
        };
    }
}