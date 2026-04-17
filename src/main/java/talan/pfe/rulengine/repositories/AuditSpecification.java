package talan.pfe.rulengine.repositories;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import talan.pfe.rulengine.entites.AuditLog;
import talan.pfe.rulengine.enums.AuditAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditSpecification {

    public static Specification<AuditLog> withFilters(
            Long tenantId,
            AuditAction action,
            String entityType,
            LocalDateTime from,
            LocalDateTime to) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (tenantId != null)
                predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            if (action != null)
                predicates.add(cb.equal(root.get("action"), action));

            if (entityType != null && !entityType.isBlank())
                predicates.add(cb.equal(root.get("entityType"), entityType));

            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));

            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}