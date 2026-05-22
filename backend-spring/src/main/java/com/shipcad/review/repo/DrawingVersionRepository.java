package com.shipcad.review.repo;

import com.shipcad.review.domain.DrawingVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingVersionRepository extends JpaRepository<DrawingVersion, String> {
    List<DrawingVersion> findByDrawingId(String drawingId);
}
