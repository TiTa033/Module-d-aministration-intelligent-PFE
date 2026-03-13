package talan.pfe.rulengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.Rule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findAllByRuleSetIdOrderByPriorityAsc(UUID ruleSetId);

    Optional<Rule> findByIdAndRuleSetId(UUID id, UUID ruleSetId);

    boolean existsByNameAndRuleSetId(String name, UUID ruleSetId);

    boolean existsByNameAndRuleSetIdAndIdNot(String name, UUID ruleSetId, UUID id);

    boolean existsByPriorityAndRuleSetId(Integer priority, UUID ruleSetId);

    boolean existsByPriorityAndRuleSetIdAndIdNot(
            Integer priority, UUID ruleSetId, UUID id);

    @Query("SELECT r FROM Rule r WHERE r.ruleSet.id = :ruleSetId " +
            "AND (:search = '' OR LOWER(r.name) " +
            "LIKE LOWER(CONCAT('%', :search, '%')))" +
            "AND (:enabled IS NULL OR r.enabled = :enabled)")
    Page<Rule> findAllByRuleSetWithFilters(
            @Param("ruleSetId") UUID ruleSetId,
            @Param("search") String search,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}