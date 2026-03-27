package talan.pfe.rulengine.entites;

import jakarta.persistence.*;
import lombok.*;
import talan.pfe.rulengine.enums.EvaluationStrategy;
import talan.pfe.rulengine.enums.RuleSetStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "rule_sets",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"name", "tenant_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_set_seq_gen")
    @SequenceGenerator(
            name = "rule_set_seq_gen",
            sequenceName = "rule_set_seq",
            allocationSize = 1
    )
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_strategy", nullable = false)
    private EvaluationStrategy evaluationStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RuleSetStatus status;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    @Builder.Default
    private List<Rule> rules = new ArrayList<>();

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleSetVersion> versions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currentVersion = 1;
        if (this.status == null) {
            this.status = RuleSetStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }



}
