package com.shipcad.review.repo;

import com.shipcad.review.domain.KnowledgeClause;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeClauseRepository extends JpaRepository<KnowledgeClause, String> {
    Optional<KnowledgeClause> findByCode(String code);
}
