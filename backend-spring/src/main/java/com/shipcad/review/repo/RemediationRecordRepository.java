package com.shipcad.review.repo;

import com.shipcad.review.domain.RemediationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationRecordRepository extends JpaRepository<RemediationRecord, String> {
}
