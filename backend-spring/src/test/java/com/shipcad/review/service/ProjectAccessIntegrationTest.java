package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.Project;
import com.shipcad.review.domain.ProjectMember;
import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.AuditLogRepository;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ProjectMemberRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewIssueRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-access-test;DB_CLOSE_DELAY=-1",
        "spring.profiles.active=test",
        "shipcad.security.seed-dev-users=false"
})
class ProjectAccessIntegrationTest {
    @Autowired
    private ProjectAccessService access;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ProjectRepository projects;
    @Autowired
    private ProjectMemberRepository members;
    @Autowired
    private DrawingRepository drawings;
    @Autowired
    private DrawingVersionRepository versions;
    @Autowired
    private ReviewTaskRepository tasks;
    @Autowired
    private ReviewIssueRepository issues;
    @Autowired
    private ReportDocumentRepository reports;
    @Autowired
    private AuditLogRepository auditLogs;

    private AppUser admin;
    private AppUser engineer;
    private AppUser viewer;
    private Project engineeringProject;
    private Project viewerProject;
    private DrawingVersion engineeringVersion;
    private DrawingVersion viewerVersion;
    private ReviewTask engineeringTask;
    private ReviewIssue engineeringIssue;
    private ReportDocument engineeringReport;

    @BeforeEach
    void setUp() {
        reports.deleteAll();
        issues.deleteAll();
        tasks.deleteAll();
        versions.deleteAll();
        drawings.deleteAll();
        members.deleteAll();
        projects.deleteAll();
        auditLogs.deleteAll();
        users.deleteAll();

        admin = saveUser("scope_admin", UserRole.ADMIN);
        engineer = saveUser("scope_engineer", UserRole.DESIGN_ENGINEER);
        viewer = saveUser("scope_viewer", UserRole.VIEWER);

        engineeringProject = saveProject("project_engineering", "Engineering Project");
        viewerProject = saveProject("project_viewer", "Viewer Project");
        saveMember(engineeringProject.id, engineer.id);
        saveMember(viewerProject.id, viewer.id);

        Drawing engineeringDrawing = saveDrawing("drawing_engineering", engineeringProject.id);
        Drawing viewerDrawing = saveDrawing("drawing_viewer", viewerProject.id);
        engineeringVersion = saveVersion("version_engineering", engineeringDrawing.id);
        viewerVersion = saveVersion("version_viewer", viewerDrawing.id);
        engineeringTask = saveTask("task_engineering", engineeringVersion.id);
        engineeringIssue = saveIssue("issue_engineering", engineeringTask.id, engineeringVersion.id);
        engineeringReport = saveReport("report_engineering", engineeringTask.id, engineeringVersion.id);
    }

    @Test
    void nonAdminOnlySeesAssignedProjectGraphWhileAdminSeesAll() {
        assertThat(access.listProjects(admin)).hasSize(2);
        assertThat(access.listProjects(engineer)).extracting(project -> project.id)
                .containsExactly(engineeringProject.id);
        assertThat(access.listDrawings(engineer, null)).extracting(drawing -> drawing.projectId)
                .containsOnly(engineeringProject.id);
        assertThat(access.listVersions(engineer, null)).extracting(version -> version.id)
                .containsExactly(engineeringVersion.id);
        assertThat(access.listTasks(engineer, null)).extracting(task -> task.id)
                .containsExactly(engineeringTask.id);
        assertThat(access.listIssues(engineer, null, null)).extracting(issue -> issue.id)
                .containsExactly(engineeringIssue.id);

        assertThat(access.requireVersion(engineer, engineeringVersion.id).id).isEqualTo(engineeringVersion.id);
        assertThat(access.requireReport(engineer, engineeringReport.id).id).isEqualTo(engineeringReport.id);
        assertThatThrownBy(() -> access.requireVersion(engineer, viewerVersion.id))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.actor).isEqualTo(engineer.username);
            assertThat(log.action).isEqualTo("DATA_ACCESS_DENIED");
            assertThat(log.targetType).isEqualTo("project");
            assertThat(log.targetId).isEqualTo(viewerProject.id);
        });
    }

    @Test
    void membershipGrantAndRemovalImmediatelyChangeVisibility() {
        assertThat(access.listProjects(viewer)).extracting(project -> project.id)
                .containsExactly(viewerProject.id);

        access.addMember(engineeringProject.id, viewer.id, admin);
        assertThat(access.listProjects(viewer)).extracting(project -> project.id)
                .containsExactlyInAnyOrder(viewerProject.id, engineeringProject.id);
        assertThat(access.listMembers(engineeringProject.id)).anySatisfy(member -> {
            assertThat(member.userId()).isEqualTo(viewer.id);
            assertThat(member.username()).isEqualTo(viewer.username);
        });

        access.removeMember(engineeringProject.id, viewer.id, admin);
        assertThat(access.listProjects(viewer)).extracting(project -> project.id)
                .containsExactly(viewerProject.id);
        assertThatThrownBy(() -> access.requireTask(viewer, engineeringTask.id))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private AppUser saveUser(String username, UserRole role) {
        AppUser user = new AppUser();
        user.id = "user_" + username;
        user.username = username;
        user.displayName = username;
        user.passwordHash = "not-used";
        user.role = role;
        user.enabled = true;
        user.createdAt = Ids.now();
        user.updatedAt = user.createdAt;
        user.passwordChangedAt = user.createdAt;
        return users.save(user);
    }

    private Project saveProject(String id, String name) {
        Project project = new Project();
        project.id = id;
        project.name = name;
        project.createdAt = Ids.now();
        return projects.save(project);
    }

    private void saveMember(String projectId, String userId) {
        ProjectMember member = new ProjectMember();
        member.id = "member_" + projectId + "_" + userId;
        member.projectId = projectId;
        member.userId = userId;
        member.createdAt = Ids.now();
        member.createdBy = admin.username;
        members.save(member);
    }

    private Drawing saveDrawing(String id, String projectId) {
        Drawing drawing = new Drawing();
        drawing.id = id;
        drawing.projectId = projectId;
        drawing.drawingNo = id;
        drawing.title = id;
        drawing.createdAt = Ids.now();
        return drawings.save(drawing);
    }

    private DrawingVersion saveVersion(String id, String drawingId) {
        DrawingVersion version = new DrawingVersion();
        version.id = id;
        version.drawingId = drawingId;
        version.versionNo = "V1";
        version.fileName = id + ".dxf";
        version.parseStatus = "SUCCESS";
        version.parseSummaryJson = "{}";
        return versions.save(version);
    }

    private ReviewTask saveTask(String id, String versionId) {
        ReviewTask task = new ReviewTask();
        task.id = id;
        task.versionId = versionId;
        task.status = "FINISHED";
        task.stage = "FINISHED";
        return tasks.save(task);
    }

    private ReviewIssue saveIssue(String id, String taskId, String versionId) {
        ReviewIssue issue = new ReviewIssue();
        issue.id = id;
        issue.taskId = taskId;
        issue.versionId = versionId;
        issue.ruleCode = "SCOPE_TEST";
        issue.title = "Scope test";
        issue.severity = Severity.LOW;
        issue.status = IssueStatus.OPEN;
        issue.createdAt = Ids.now();
        issue.updatedAt = issue.createdAt;
        return issues.save(issue);
    }

    private ReportDocument saveReport(String id, String taskId, String versionId) {
        ReportDocument report = new ReportDocument();
        report.id = id;
        report.taskId = taskId;
        report.versionId = versionId;
        report.content = "# Scope test";
        report.createdAt = Ids.now();
        return reports.save(report);
    }
}
