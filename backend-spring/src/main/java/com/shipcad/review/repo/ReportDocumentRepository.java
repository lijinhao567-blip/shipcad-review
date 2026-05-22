package com.shipcad.review.repo;

import com.shipcad.review.domain.ReportDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDocumentRepository extends JpaRepository<ReportDocument, String> {
}
