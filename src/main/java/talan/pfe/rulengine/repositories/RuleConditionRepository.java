package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.RuleCondition;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleConditionRepository extends JpaRepository<RuleCondition, Long> {

    List<RuleCondition> findAllByRuleIdOrderByIdAsc(Long ruleId);
}

