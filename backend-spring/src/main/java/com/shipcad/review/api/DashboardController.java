package com.shipcad.review.api;

import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ProjectAccessService;
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
    private final ProjectAccessService projectAccess;

    public DashboardController(AuthService auth, ProjectAccessService projectAccess) {
        super(auth);
        this.projectAccess = projectAccess;
    }

    @GetMapping
    public Map<String, Object> dashboard(@RequestHeader("Authorization") String authorization) {
        var actor = user(authorization);
        var visibleProjects = projectAccess.listProjects(actor);
        var visibleDrawings = projectAccess.listDrawings(actor, null);
        var visibleVersions = projectAccess.listVersions(actor, null);
        var visibleTasks = projectAccess.listTasks(actor, null);
        List<ReviewIssue> allIssues = projectAccess.listIssues(actor, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectCount", visibleProjects.size());
        result.put("drawingCount", visibleDrawings.size());
        result.put("versionCount", visibleVersions.size());
        result.put("taskCount", visibleTasks.size());
        result.put("openIssueCount", allIssues.stream().filter(issue -> issue.status != IssueStatus.CLOSED).count());
        result.put("issueCountBySeverity", countBy(allIssues, issue -> issue.severity.name()));
        result.put("issueCountByStatus", countBy(allIssues, issue -> issue.status.name()));
        result.put("issueCountByRule", countBy(allIssues, issue -> issue.ruleCode));
        return result;
    }

    private Map<String, Long> countBy(List<ReviewIssue> source, java.util.function.Function<ReviewIssue, String> key) {
        return source.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }
}
