package talan.pfe.rulengine.entites;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import talan.pfe.rulengine.enums.EvaluationStrategy;


@Entity
@Table(name = "evaluation_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", nullable = false, columnDefinition = "jsonb")
    private String outputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_rules", columnDefinition = "jsonb")
    private String matchedRules;

    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_used", nullable = false)
    private EvaluationStrategy strategyUsed;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_request_id", nullable = false, unique = true)
    private EvaluationRequest evaluationRequest;

    @PrePersist
    protected void onCreate() {
        this.evaluatedAt = LocalDateTime.now();
    }
}