package com.shipcad.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.RemediationRecord;
import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.ReviewPlatformService;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ReviewControllerTest {
    @Test
    void downloadReportReturnsMarkdownAttachment() {
        AuthService auth = mock(AuthService.class);
        ReviewRuleRepository rules = mock(ReviewRuleRepository.class);
        ReportDocumentRepository reports = mock(ReportDocumentRepository.class);
        ReviewPlatformService platform = mock(ReviewPlatformService.class);
        AuthorizationService access = mock(AuthorizationService.class);
        ReviewController controller = new ReviewController(auth, rules, reports, platform, access);

        ReportDocument report = new ReportDocument();
        report.id = "report_1";
        report.taskId = "task_1";
        report.versionId = "version_1";
        report.content = "# Review Report\n\n- issue: LAYER_NAME_STANDARD\n";

        when(auth.requireUser("Bearer token")).thenReturn(new AppUser());
        when(reports.findById("report_1")).thenReturn(Optional.of(report));

        ResponseEntity<String> response = controller.downloadReport("Bearer token", "report_1");

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/markdown;charset=UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"shipcad-review-report-report_1.md\"");
        assertThat(response.getBody()).isEqualTo(report.content);
    }

    @Test
    void issueRemediationsReturnsTimeline() {
        AuthService auth = mock(AuthService.class);
        ReviewRuleRepository rules = mock(ReviewRuleRepository.class);
        ReportDocumentRepository reports = mock(ReportDocumentRepository.class);
        ReviewPlatformService platform = mock(ReviewPlatformService.class);
        AuthorizationService access = mock(AuthorizationService.class);
        ReviewController controller = new ReviewController(auth, rules, reports, platform, access);

        RemediationRecord record = new RemediationRecord();
        record.id = "remediation_1";
        record.issueId = "issue_1";
        record.action = "SUBMIT_FOR_REVIEW";
        record.fromStatus = "IN_PROGRESS";
        record.toStatus = "READY_FOR_REVIEW";

        when(auth.requireUser("Bearer token")).thenReturn(new AppUser());
        when(platform.listIssueRemediations("issue_1")).thenReturn(List.of(record));

        List<RemediationRecord> result = controller.issueRemediations("Bearer token", "issue_1");

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id).isEqualTo("remediation_1");
            assertThat(item.action).isEqualTo("SUBMIT_FOR_REVIEW");
            assertThat(item.toStatus).isEqualTo("READY_FOR_REVIEW");
        });
    }
}
