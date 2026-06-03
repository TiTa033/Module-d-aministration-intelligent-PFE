package talan.pfe.rulengine.entites;

import jakarta.persistence.*;
import lombok.*;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_config_seq_gen")
    @SequenceGenerator(
            name = "alert_config_seq_gen",
            sequenceName = "alert_config_seq",
            allocationSize = 1
    )
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false)
    private AlertMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private AlertCondition conditionType;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    /** Fenêtre d'évaluation en heures (ex: 1, 24) */
    @Column(name = "window_hours", nullable = false)
    private Integer windowHours;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Null = alerte globale au niveau tenant */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id")
    private RuleSet ruleSet;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (!this.enabled) this.enabled = true;
    }
}
