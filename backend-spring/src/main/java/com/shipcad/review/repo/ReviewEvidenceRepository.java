package com.shipcad.review.repo;

import com.shipcad.review.domain.ReviewEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewEvidenceRepository extends JpaRepository<ReviewEvidence, String> {
    List<ReviewEvidence> findByIssueId(String issueId);
    List<ReviewEvidence> findByIssueIdIn(List<String> issueIds);
    List<ReviewEvidence> findByTaskId(String taskId);
    List<ReviewEvidence> findByVersionId(String versionId);
}
