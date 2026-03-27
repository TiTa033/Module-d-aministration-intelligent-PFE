package talan.pfe.rulengine.entites;


import jakarta.persistence.*;
import lombok.*;
import talan.pfe.rulengine.enums.ActionType;
@Entity
@Table(name = "rule_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleAction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_action_seq_gen")
    @SequenceGenerator(
            name = "rule_action_seq_gen",
            sequenceName = "rule_action_seq",
            allocationSize = 1
    )
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "output_key", nullable = false)
    private String outputKey;

    @Column(name = "output_value", nullable = false)
    private String outputValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;


    public Object execute() {
        return switch (actionType) {
            case SET_VALUE      -> outputValue;
            case SET_FLAG       -> Boolean.parseBoolean(outputValue);
            case SET_SCORE      -> Double.parseDouble(outputValue);
            case REJECT         -> "REJECTED";
            case APPROVE        -> "APPROVED";
            case REQUIRE_REVIEW -> "REVIEW";
        };
    }
}