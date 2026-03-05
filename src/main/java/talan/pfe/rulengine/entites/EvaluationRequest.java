package talan.pfe.rulengine.entites;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluation_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private String inputPayload;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @OneToOne(mappedBy = "evaluationRequest",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            optional = false)
    private EvaluationResult result;

    @PrePersist
    protected void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }
}