package com.shipcad.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "project_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_member_project_user",
                columnNames = {"projectId", "userId"}
        ),
        indexes = {
                @Index(name = "idx_project_member_project_id", columnList = "projectId"),
                @Index(name = "idx_project_member_user_id", columnList = "userId")
        }
)
public class ProjectMember {
    @Id
    public String id;
    @Column(nullable = false)
    public String projectId;
    @Column(nullable = false)
    public String userId;
    @Column(nullable = false)
    public Instant createdAt;
    @Column(nullable = false, length = 50)
    public String createdBy;
}
