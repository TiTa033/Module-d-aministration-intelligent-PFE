package talan.pfe.rulengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import talan.pfe.rulengine.entites.EvaluationRequest;

@Repository
public interface EvaluationRequestRepository extends JpaRepository<EvaluationRequest, Long> {
}
