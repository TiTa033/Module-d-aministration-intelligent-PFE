package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.AiInsight;
import talan.pfe.rulengine.enums.InsightStatus;
import talan.pfe.rulengine.enums.InsightType;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    List<AiInsight> findByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
            Long ruleSetId, Long tenantId, InsightType type);

    Optional<AiInsight> findFirstByRuleSetIdAndTenantIdAndTypeOrderByGeneratedAtDesc(
            Long ruleSetId, Long tenantId, InsightType type);

    List<AiInsight> findByTenantIdAndTypeAndStatusOrderByGeneratedAtDesc(
            Long tenantId, InsightType type, InsightStatus status);

    List<AiInsight> findByTenantIdAndTypeOrderByGeneratedAtDesc(Long tenantId, InsightType type);

    Optional<AiInsight> findFirstByTenantIdAndTypeOrderByGeneratedAtDesc(Long tenantId, InsightType type);

    boolean existsByTenantIdAndTypeAndTitle(Long tenantId, InsightType type, String title);
}
