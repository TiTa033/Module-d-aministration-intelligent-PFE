package talan.pfe.rulengine.entites;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import talan.pfe.rulengine.enums.AgentType;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_insights",
        indexes = {
                @Index(name = "idx_insight_tenant", columnList = "tenant_id"),
                @Index(name = "idx_insight_ruleset", columnList = "rule_set_id"),
                @Index(name = "idx_insight_type", columnList = "type"),
                @Index(name = "idx_insight_status", columnList = "status"),
                @Index(name = "idx_insight_agent", columnList = "agent_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_insight_seq_gen")
    @SequenceGenerator(
            name = "ai_insight_seq_gen",
            sequenceName = "ai_insight_seq",
            allocationSize = 1
    )
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private InsightType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InsightStatus status;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestion", columnDefinition = "jsonb")
    private String suggestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private String contextData;

    @Column(name = "confidence", nullable = false)
    private Float confidence;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id")
    private RuleSet ruleSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @PrePersist
    protected void onCreate() {
        this.generatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = InsightStatus.PENDING;
        }
    }

    public void accept(User user) {
        this.status = InsightStatus.ACCEPTED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = user;
    }

    public void reject(User user) {
        this.status = InsightStatus.REJECTED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = user;
    }





}