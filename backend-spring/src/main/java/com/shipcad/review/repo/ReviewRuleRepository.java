package com.shipcad.review.repo;

import com.shipcad.review.domain.ReviewRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRuleRepository extends JpaRepository<ReviewRule, String> {
    List<ReviewRule> findByEnabledTrue();
}
