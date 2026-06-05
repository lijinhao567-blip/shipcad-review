package com.shipcad.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ReviewPlatformService;
import java.util.Optional;
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
        ReviewController controller = new ReviewController(auth, rules, reports, platform);

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
}
