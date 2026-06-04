package com.shipcad.review.repo;

import com.shipcad.review.domain.ReviewTaskStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTaskStepRepository extends JpaRepository<ReviewTaskStep, String> {
    List<ReviewTaskStep> findByTaskIdOrderByStepOrderAsc(String taskId);
    List<ReviewTaskStep> findByTaskIdInOrderByTaskIdAscStepOrderAsc(List<String> taskIds);
}
