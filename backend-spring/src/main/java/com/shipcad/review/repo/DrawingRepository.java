package com.shipcad.review.repo;

import com.shipcad.review.domain.Drawing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingRepository extends JpaRepository<Drawing, String> {
    List<Drawing> findByProjectId(String projectId);

    List<Drawing> findByProjectIdIn(List<String> projectIds);
}
