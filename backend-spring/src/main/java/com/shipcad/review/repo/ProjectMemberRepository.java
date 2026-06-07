package com.shipcad.review.repo;

import com.shipcad.review.domain.ProjectMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {
    boolean existsByProjectIdAndUserId(String projectId, String userId);

    Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);

    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(String projectId);

    List<ProjectMember> findByUserId(String userId);
}
