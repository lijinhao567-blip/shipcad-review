package com.shipcad.review.repo;

import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ReviewIssue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, String> {
    List<ReviewIssue> findByTaskId(String taskId);
    List<ReviewIssue> findByVersionId(String versionId);
    long countByStatusNot(IssueStatus status);
}
