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

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {

    List<Rule> findAllByRuleSetIdOrderByPriorityAsc(Long ruleSetId);

    Optional<Rule> findByIdAndRuleSetId(Long id, Long ruleSetId);

    boolean existsByNameAndRuleSetId(String name, Long ruleSetId);

    boolean existsByNameAndRuleSetIdAndIdNot(String name, Long ruleSetId, Long id);

    boolean existsByPriorityAndRuleSetId(Integer priority, Long ruleSetId);

    boolean existsByPriorityAndRuleSetIdAndIdNot(
            Integer priority, Long ruleSetId, Long id);

    @Query("SELECT r FROM Rule r WHERE r.ruleSet.id = :ruleSetId " +
            "AND (:search = '' OR LOWER(r.name) " +
            "LIKE LOWER(CONCAT('%', :search, '%')))" +
            "AND (:enabled IS NULL OR r.enabled = :enabled)")
    Page<Rule> findAllByRuleSetWithFilters(
            @Param("ruleSetId") Long ruleSetId,
            @Param("search") String search,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}