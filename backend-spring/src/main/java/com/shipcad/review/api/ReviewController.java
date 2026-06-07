package com.shipcad.review.api;

import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.domain.AiExplanation;
import com.shipcad.review.domain.RemediationRecord;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.domain.ReviewTaskStep;
import com.shipcad.review.dto.ApiDtos.IssueUpdateRequest;
import com.shipcad.review.dto.ApiDtos.ReportRequest;
import com.shipcad.review.dto.ApiDtos.ReviewTaskRequest;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.ReviewPlatformService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController extends BaseController {
    private final ReviewRuleRepository rules;
    private final ReportDocumentRepository reports;
    private final ReviewPlatformService platform;
    private final AuthorizationService access;

    public ReviewController(AuthService auth, ReviewRuleRepository rules,
                            ReportDocumentRepository reports, ReviewPlatformService platform,
                            AuthorizationService access) {
        super(auth);
        this.rules = rules;
        this.reports = reports;
        this.platform = platform;
        this.access = access;
    }

    @GetMapping("/review-tasks")
    public List<ReviewTask> tasks(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String versionId) {
        user(authorization);
        return platform.listReviewTasks(versionId);
    }

    @GetMapping("/review-tasks/{taskId}")
    public ReviewTask task(@RequestHeader("Authorization") String authorization, @PathVariable String taskId) {
        user(authorization);
        return platform.getReviewTask(taskId);
    }

    @GetMapping("/review-tasks/{taskId}/steps")
    public List<ReviewTaskStep> taskSteps(@RequestHeader("Authorization") String authorization, @PathVariable String taskId) {
        user(authorization);
        return platform.listReviewTaskSteps(taskId);
    }

    @PostMapping("/review-tasks")
    public ReviewTask createTask(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ReviewTaskRequest request) {
        var actor = user(authorization);
        access.require(actor, Permission.REVIEW_EXECUTE);
        return platform.createReviewTask(request, actor);
    }

    @PostMapping("/review-tasks/{taskId}/retry")
    public ReviewTask retryTask(@RequestHeader("Authorization") String authorization, @PathVariable String taskId) {
        var actor = user(authorization);
        access.require(actor, Permission.REVIEW_EXECUTE);
        return platform.retryReviewTask(taskId, actor);
    }

    @GetMapping("/issues")
    public List<ReviewIssue> issues(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String taskId,
                                    @RequestParam(required = false) String versionId) {
        user(authorization);
        return platform.listIssues(taskId, versionId);
    }

    @GetMapping("/issues/{issueId}/evidences")
    public List<ReviewEvidence> issueEvidences(@RequestHeader("Authorization") String authorization, @PathVariable String issueId) {
        user(authorization);
        return platform.listIssueEvidence(issueId);
    }

    @GetMapping("/issues/{issueId}/remediations")
    public List<RemediationRecord> issueRemediations(@RequestHeader("Authorization") String authorization, @PathVariable String issueId) {
        user(authorization);
        return platform.listIssueRemediations(issueId);
    }

    @GetMapping("/issues/{issueId}/ai-explanation")
    public AiExplanation issueAiExplanation(@RequestHeader("Authorization") String authorization, @PathVariable String issueId) {
        user(authorization);
        return platform.explainIssue(issueId);
    }

    @PatchMapping("/issues/{issueId}")
    public ReviewIssue updateIssue(@RequestHeader("Authorization") String authorization, @PathVariable String issueId,
                                   @RequestBody IssueUpdateRequest request) {
        var actor = user(authorization);
        access.requireIssueUpdate(actor, request.status());
        return platform.updateIssue(issueId, request, actor);
    }

    @GetMapping("/rules")
    public List<ReviewRule> rules(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        return rules.findAll();
    }

    @PostMapping("/reports")
    public ReportDocument createReport(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ReportRequest request) {
        var actor = user(authorization);
        access.require(actor, Permission.REPORT_GENERATE);
        return platform.createReport(request.taskId(), actor);
    }

    @GetMapping("/reports/{reportId}")
    public ReportDocument report(@RequestHeader("Authorization") String authorization, @PathVariable String reportId) {
        user(authorization);
        return reports.findById(reportId).orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }

    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<String> downloadReport(@RequestHeader("Authorization") String authorization, @PathVariable String reportId) {
        user(authorization);
        ReportDocument report = reports.findById(reportId).orElseThrow(() -> new IllegalArgumentException("报告不存在"));
        String fileName = "shipcad-review-report-" + report.id + ".md";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(report.content == null ? "" : report.content);
    }
}
