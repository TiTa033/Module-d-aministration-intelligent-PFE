package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.RuleAction;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleActionRepository extends JpaRepository<RuleAction, UUID> {

    List<RuleAction> findAllByRuleIdOrderByIdAsc(UUID ruleId);
}

