package talan.pfe.rulengine.entites;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rule_set_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleSetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "change_note")
    private String changeNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}