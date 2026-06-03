package com.shipcad.review.api;

import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.dto.ApiDtos.IssueUpdateRequest;
import com.shipcad.review.dto.ApiDtos.ReportRequest;
import com.shipcad.review.dto.ApiDtos.ReviewTaskRequest;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ReviewPlatformService;
import jakarta.validation.Valid;
import java.util.List;
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
    private final ReviewTaskRepository tasks;
    private final ReviewRuleRepository rules;
    private final ReportDocumentRepository reports;
    private final ReviewPlatformService platform;

    public ReviewController(AuthService auth, ReviewTaskRepository tasks, ReviewRuleRepository rules,
                            ReportDocumentRepository reports, ReviewPlatformService platform) {
        super(auth);
        this.tasks = tasks;
        this.rules = rules;
        this.reports = reports;
        this.platform = platform;
    }

    @GetMapping("/review-tasks")
    public List<ReviewTask> tasks(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String versionId) {
        user(authorization);
        return versionId == null || versionId.isBlank() ? tasks.findAll() : tasks.findByVersionId(versionId);
    }

    @GetMapping("/review-tasks/{taskId}")
    public ReviewTask task(@RequestHeader("Authorization") String authorization, @PathVariable String taskId) {
        user(authorization);
        return tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
    }

    @PostMapping("/review-tasks")
    public ReviewTask createTask(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ReviewTaskRequest request) {
        return platform.createReviewTask(request.versionId(), user(authorization));
    }

    @PostMapping("/review-tasks/{taskId}/retry")
    public ReviewTask retryTask(@RequestHeader("Authorization") String authorization, @PathVariable String taskId) {
        return platform.retryReviewTask(taskId, user(authorization));
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

    @PatchMapping("/issues/{issueId}")
    public ReviewIssue updateIssue(@RequestHeader("Authorization") String authorization, @PathVariable String issueId,
                                   @RequestBody IssueUpdateRequest request) {
        return platform.updateIssue(issueId, request, user(authorization));
    }

    @GetMapping("/rules")
    public List<ReviewRule> rules(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        return rules.findAll();
    }

    @PostMapping("/reports")
    public ReportDocument createReport(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ReportRequest request) {
        return platform.createReport(request.taskId(), user(authorization));
    }

    @GetMapping("/reports/{reportId}")
    public ReportDocument report(@RequestHeader("Authorization") String authorization, @PathVariable String reportId) {
        user(authorization);
        return reports.findById(reportId).orElseThrow(() -> new IllegalArgumentException("报告不存在"));
    }
}
