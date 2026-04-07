package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.RuleSetVersion;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleSetVersionRepository extends JpaRepository<RuleSetVersion, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<RuleSetVersion> findByRuleSetIdOrderByVersionNumberDesc(Long ruleSetId);

    Optional<RuleSetVersion> findByRuleSetIdAndVersionNumber(Long ruleSetId,
                                                             Integer versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM RuleSetVersion v "
            + "WHERE v.ruleSet.id = :ruleSetId")
    int findMaxVersionNumber(@Param("ruleSetId") Long ruleSetId);
}
