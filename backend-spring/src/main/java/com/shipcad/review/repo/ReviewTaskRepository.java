package com.shipcad.review.repo;

import com.shipcad.review.domain.ReviewTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, String> {
    List<ReviewTask> findByVersionId(String versionId);

    List<ReviewTask> findByVersionIdIn(List<String> versionIds);
}
