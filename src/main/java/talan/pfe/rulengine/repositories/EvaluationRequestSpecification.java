package talan.pfe.rulengine.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import talan.pfe.rulengine.entites.EvaluationRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EvaluationRequestSpecification {

    public static Specification<EvaluationRequest> withFilters(
            Long tenantId,
            Long ruleSetId,
            LocalDateTime from,
            LocalDateTime to) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by tenant
            if (tenantId != null)
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            if (ruleSetId != null)
                predicates.add(cb.equal(root.get("ruleSet").get("id"), ruleSetId));

            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), from));

            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("requestedAt"), to));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}