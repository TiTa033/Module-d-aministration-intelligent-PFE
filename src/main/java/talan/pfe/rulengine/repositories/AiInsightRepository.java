package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.enums.InsightType;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    List<AiInsight> findByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
            Long ruleSetId, Long tenantId, InsightType type);

    Optional<AiInsight> findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
            Long ruleSetId, Long tenantId, InsightType type);
}
