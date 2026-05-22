package com.shipcad.review.repo;

import com.shipcad.review.domain.ParsedEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParsedEntityRepository extends JpaRepository<ParsedEntity, String> {
    List<ParsedEntity> findByVersionId(String versionId);
    void deleteByVersionId(String versionId);
}
