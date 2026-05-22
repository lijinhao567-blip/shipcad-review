package com.shipcad.review.api;

import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.repo.ReviewIssueRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import com.shipcad.review.service.AuthService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController extends BaseController {
    private final ProjectRepository projects;
    private final DrawingRepository drawings;
    private final DrawingVersionRepository versions;
    private final ReviewTaskRepository tasks;
    private final ReviewIssueRepository issues;

    public DashboardController(AuthService auth, ProjectRepository projects, DrawingRepository drawings, DrawingVersionRepository versions,
                               ReviewTaskRepository tasks, ReviewIssueRepository issues) {
        super(auth);
        this.projects = projects;
        this.drawings = drawings;
        this.versions = versions;
        this.tasks = tasks;
        this.issues = issues;
    }

    @GetMapping
    public Map<String, Object> dashboard(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        List<ReviewIssue> allIssues = issues.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectCount", projects.count());
        result.put("drawingCount", drawings.count());
        result.put("versionCount", versions.count());
        result.put("taskCount", tasks.count());
        result.put("openIssueCount", issues.countByStatusNot(IssueStatus.CLOSED));
        result.put("issueCountBySeverity", countBy(allIssues, issue -> issue.severity.name()));
        result.put("issueCountByStatus", countBy(allIssues, issue -> issue.status.name()));
        result.put("issueCountByRule", countBy(allIssues, issue -> issue.ruleCode));
        return result;
    }

    private Map<String, Long> countBy(List<ReviewIssue> source, java.util.function.Function<ReviewIssue, String> key) {
        return source.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }
}
