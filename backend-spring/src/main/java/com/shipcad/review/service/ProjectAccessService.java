package com.shipcad.review.service;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.Project;
import com.shipcad.review.domain.ProjectMember;
import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.dto.ApiDtos.ProjectMemberView;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ProjectMemberRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewIssueRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final AppUserRepository users;
    private final DrawingRepository drawings;
    private final DrawingVersionRepository versions;
    private final ReviewTaskRepository tasks;
    private final ReviewIssueRepository issues;
    private final ReportDocumentRepository reports;
    private final AuditService audit;

    public ProjectAccessService(
            ProjectRepository projects,
            ProjectMemberRepository members,
            AppUserRepository users,
            DrawingRepository drawings,
            DrawingVersionRepository versions,
            ReviewTaskRepository tasks,
            ReviewIssueRepository issues,
            ReportDocumentRepository reports,
            AuditService audit
    ) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.drawings = drawings;
        this.versions = versions;
        this.tasks = tasks;
        this.issues = issues;
        this.reports = reports;
        this.audit = audit;
    }

    public List<Project> listProjects(AppUser actor) {
        if (isAdmin(actor)) {
            return projects.findAll();
        }
        List<String> projectIds = accessibleProjectIds(actor);
        return projectIds.isEmpty() ? List.of() : projects.findAllById(projectIds);
    }

    public List<Drawing> listDrawings(AppUser actor, String projectId) {
        if (projectId != null && !projectId.isBlank()) {
            requireProject(actor, projectId);
            return drawings.findByProjectId(projectId);
        }
        if (isAdmin(actor)) {
            return drawings.findAll();
        }
        List<String> projectIds = accessibleProjectIds(actor);
        return projectIds.isEmpty() ? List.of() : drawings.findByProjectIdIn(projectIds);
    }

    public List<DrawingVersion> listVersions(AppUser actor, String drawingId) {
        if (drawingId != null && !drawingId.isBlank()) {
            requireDrawing(actor, drawingId);
            return versions.findByDrawingId(drawingId);
        }
        if (isAdmin(actor)) {
            return versions.findAll();
        }
        List<String> drawingIds = accessibleDrawingIds(actor);
        return drawingIds.isEmpty() ? List.of() : versions.findByDrawingIdIn(drawingIds);
    }

    public List<ReviewTask> listTasks(AppUser actor, String versionId) {
        if (versionId != null && !versionId.isBlank()) {
            requireVersion(actor, versionId);
            return tasks.findByVersionId(versionId);
        }
        if (isAdmin(actor)) {
            return tasks.findAll();
        }
        List<String> versionIds = accessibleVersionIds(actor);
        return versionIds.isEmpty() ? List.of() : tasks.findByVersionIdIn(versionIds);
    }

    public List<ReviewIssue> listIssues(AppUser actor, String taskId, String versionId) {
        if (taskId != null && !taskId.isBlank()) {
            requireTask(actor, taskId);
            return issues.findByTaskId(taskId);
        }
        if (versionId != null && !versionId.isBlank()) {
            requireVersion(actor, versionId);
            return issues.findByVersionId(versionId);
        }
        if (isAdmin(actor)) {
            return issues.findAll();
        }
        List<String> versionIds = accessibleVersionIds(actor);
        return versionIds.isEmpty() ? List.of() : issues.findByVersionIdIn(versionIds);
    }

    public Project requireProject(AppUser actor, String projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        if (!isAdmin(actor) && !members.existsByProjectIdAndUserId(projectId, actor.id)) {
            deny(actor, "project", projectId);
        }
        return project;
    }

    public Drawing requireDrawing(AppUser actor, String drawingId) {
        Drawing drawing = drawings.findById(drawingId)
                .orElseThrow(() -> new IllegalArgumentException("图纸不存在"));
        requireProject(actor, drawing.projectId);
        return drawing;
    }

    public DrawingVersion requireVersion(AppUser actor, String versionId) {
        DrawingVersion version = versions.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        requireDrawing(actor, version.drawingId);
        return version;
    }

    public ReviewTask requireTask(AppUser actor, String taskId) {
        ReviewTask task = tasks.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        requireVersion(actor, task.versionId);
        return task;
    }

    public ReviewIssue requireIssue(AppUser actor, String issueId) {
        ReviewIssue issue = issues.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        requireVersion(actor, issue.versionId);
        return issue;
    }

    public ReportDocument requireReport(AppUser actor, String reportId) {
        ReportDocument report = reports.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        requireVersion(actor, report.versionId);
        return report;
    }

    public List<ProjectMemberView> listMembers(String projectId) {
        projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<ProjectMember> source = members.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<String, AppUser> usersById = users.findAllById(source.stream().map(member -> member.userId).toList())
                .stream()
                .collect(Collectors.toMap(user -> user.id, Function.identity()));
        return source.stream().map(member -> memberView(member, usersById.get(member.userId))).toList();
    }

    @Transactional
    public ProjectMemberView addMember(String projectId, String userId, AppUser actor) {
        projects.findById(projectId).orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        AppUser target = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        ProjectMember existing = members.findByProjectIdAndUserId(projectId, userId).orElse(null);
        if (existing != null) {
            return memberView(existing, target);
        }
        ProjectMember member = new ProjectMember();
        member.id = Ids.next("projectmember");
        member.projectId = projectId;
        member.userId = userId;
        member.createdAt = Ids.now();
        member.createdBy = actor.username;
        members.saveAndFlush(member);
        audit.record(actor.username, "PROJECT_MEMBER_ADD", "project", projectId,
                Map.of("userId", target.id, "username", target.username));
        return memberView(member, target);
    }

    @Transactional
    public void removeMember(String projectId, String userId, AppUser actor) {
        ProjectMember member = members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new IllegalArgumentException("项目成员不存在"));
        members.delete(member);
        audit.record(actor.username, "PROJECT_MEMBER_REMOVE", "project", projectId,
                Map.of("userId", userId));
    }

    public void addCreatorMembership(String projectId, AppUser actor) {
        addMember(projectId, actor.id, actor);
    }

    public List<String> accessibleVersionIds(AppUser actor) {
        if (isAdmin(actor)) {
            return versions.findAll().stream().map(version -> version.id).toList();
        }
        List<String> drawingIds = accessibleDrawingIds(actor);
        if (drawingIds.isEmpty()) {
            return List.of();
        }
        return versions.findByDrawingIdIn(drawingIds).stream().map(version -> version.id).toList();
    }

    private List<String> accessibleDrawingIds(AppUser actor) {
        List<String> projectIds = accessibleProjectIds(actor);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return drawings.findByProjectIdIn(projectIds).stream().map(drawing -> drawing.id).toList();
    }

    private List<String> accessibleProjectIds(AppUser actor) {
        if (actor == null || actor.id == null) {
            return List.of();
        }
        return members.findByUserId(actor.id).stream()
                .map(member -> member.projectId)
                .distinct()
                .toList();
    }

    private boolean isAdmin(AppUser actor) {
        return actor != null && actor.role == UserRole.ADMIN;
    }

    private void deny(AppUser actor, String targetType, String targetId) {
        String username = actor == null || actor.username == null ? "unknown" : actor.username;
        audit.record(username, "DATA_ACCESS_DENIED", targetType, targetId,
                Map.of("reason", "PROJECT_MEMBERSHIP_REQUIRED"));
        throw new ForbiddenOperationException("无权访问该项目数据");
    }

    private ProjectMemberView memberView(ProjectMember member, AppUser user) {
        return new ProjectMemberView(
                member.id,
                member.projectId,
                member.userId,
                user == null ? "" : user.username,
                user == null ? "未知用户" : user.displayName,
                user == null || user.role == null ? "" : user.role.name(),
                user != null && !Boolean.FALSE.equals(user.enabled),
                member.createdAt,
                member.createdBy
        );
    }
}
