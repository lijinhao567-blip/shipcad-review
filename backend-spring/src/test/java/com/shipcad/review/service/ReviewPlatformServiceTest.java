package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.KnowledgeClauseRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.repo.RemediationRecordRepository;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewEvidenceRepository;
import com.shipcad.review.repo.ReviewIssueRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import com.shipcad.review.repo.ReviewTaskStepRepository;
import com.shipcad.review.storage.ObjectStorageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReviewPlatformServiceTest {
    @Test
    void retryReviewTaskRejectsNonFailedTasks() {
        ProjectAccessService access = mock(ProjectAccessService.class);
        ReviewTaskRepository tasks = mock(ReviewTaskRepository.class);
        ReviewTaskQueue queue = mock(ReviewTaskQueue.class);
        AuditService audit = mock(AuditService.class);
        ReviewPlatformService service = service(access, tasks, queue, audit);
        AppUser actor = actor();
        ReviewTask finished = task("task_done", "version_1", "FINISHED");
        when(access.requireTask(actor, "task_done")).thenReturn(finished);

        assertThatThrownBy(() -> service.retryReviewTask("task_done", actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED");

        verify(tasks, never()).save(any());
        verify(queue, never()).enqueue(anyString(), anyString());
        verify(audit, never()).record(eq(actor.username), eq("REVIEW_RETRY"), anyString(), anyString(), any());
    }

    @Test
    void retryReviewTaskCreatesNewQueuedTaskWithOriginalOptions() {
        ProjectAccessService access = mock(ProjectAccessService.class);
        ReviewTaskRepository tasks = mock(ReviewTaskRepository.class);
        ReviewTaskQueue queue = mock(ReviewTaskQueue.class);
        AuditService audit = mock(AuditService.class);
        ReviewPlatformService service = service(access, tasks, queue, audit);
        AppUser actor = actor();
        ReviewTask failed = task("task_old", "version_1", "FAILED");
        failed.autoVision = true;
        failed.autoOcr = false;
        failed.forceRender = true;
        failed.visionConfidence = 0.42;
        failed.ocrConfidence = null;
        when(access.requireTask(actor, "task_old")).thenReturn(failed);
        when(access.requireVersion(actor, "version_1")).thenReturn(new DrawingVersion());

        ReviewTask retry = service.retryReviewTask("task_old", actor);

        ArgumentCaptor<ReviewTask> taskCaptor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(tasks).save(taskCaptor.capture());
        ReviewTask saved = taskCaptor.getValue();
        assertThat(retry).isSameAs(saved);
        assertThat(saved.id).startsWith("task_").isNotEqualTo("task_old");
        assertThat(saved.versionId).isEqualTo("version_1");
        assertThat(saved.status).isEqualTo("PENDING");
        assertThat(saved.stage).isEqualTo("QUEUED");
        assertThat(saved.autoVision).isTrue();
        assertThat(saved.autoOcr).isFalse();
        assertThat(saved.forceRender).isTrue();
        assertThat(saved.visionConfidence).isEqualTo(0.42);
        assertThat(saved.ocrConfidence).isEqualTo(0.5);
        assertThat(saved.errorMessage).isEmpty();

        verify(queue).enqueue(saved.id, actor.username);
        verify(audit).record(eq(actor.username), eq("REVIEW_QUEUED"), eq("task"), eq(saved.id), any());
        ArgumentCaptor<Object> detailCaptor = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq(actor.username), eq("REVIEW_RETRY"), eq("task"), eq(saved.id), detailCaptor.capture());
        assertThat(detailCaptor.getValue()).isInstanceOf(Map.class);
        Map<?, ?> detail = (Map<?, ?>) detailCaptor.getValue();
        assertThat(detail.get("sourceTaskId")).isEqualTo("task_old");
        assertThat(detail.get("versionId")).isEqualTo("version_1");
    }

    private ReviewPlatformService service(
            ProjectAccessService access,
            ReviewTaskRepository tasks,
            ReviewTaskQueue queue,
            AuditService audit
    ) {
        return new ReviewPlatformService(
                mock(ProjectRepository.class),
                mock(DrawingRepository.class),
                mock(DrawingVersionRepository.class),
                mock(ParsedEntityRepository.class),
                mock(ReviewRuleRepository.class),
                tasks,
                mock(ReviewTaskStepRepository.class),
                mock(ReviewIssueRepository.class),
                mock(ReviewEvidenceRepository.class),
                mock(KnowledgeClauseRepository.class),
                mock(RemediationRecordRepository.class),
                mock(ReportDocumentRepository.class),
                mock(CadWorkerClient.class),
                mock(VisionWorkerClient.class),
                mock(OcrWorkerClient.class),
                mock(RuleEngine.class),
                mock(ReviewReportBuilder.class),
                mock(VersionCompareService.class),
                mock(AiGateway.class),
                access,
                audit,
                new ObjectMapper(),
                queue,
                mock(ObjectStorageService.class)
        );
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.id = "user_admin";
        actor.username = "admin";
        actor.role = UserRole.ADMIN;
        actor.enabled = true;
        return actor;
    }

    private ReviewTask task(String id, String versionId, String status) {
        ReviewTask task = new ReviewTask();
        task.id = id;
        task.versionId = versionId;
        task.status = status;
        return task;
    }
}
