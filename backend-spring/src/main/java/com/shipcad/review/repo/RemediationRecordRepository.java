package com.shipcad.review.repo;

import com.shipcad.review.domain.RemediationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationRecordRepository extends JpaRepository<RemediationRecord, String> {
    List<RemediationRecord> findByIssueIdOrderByCreatedAtAsc(String issueId);
}
