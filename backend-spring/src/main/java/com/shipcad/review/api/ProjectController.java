package com.shipcad.review.api;

import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.domain.Project;
import com.shipcad.review.dto.ApiDtos.AddProjectMemberRequest;
import com.shipcad.review.dto.ApiDtos.DrawingRequest;
import com.shipcad.review.dto.ApiDtos.ProjectMemberView;
import com.shipcad.review.dto.ApiDtos.ProjectRequest;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.ProjectAccessService;
import com.shipcad.review.service.ReviewPlatformService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProjectController extends BaseController {
    private final ReviewPlatformService platform;
    private final AuthorizationService access;
    private final ProjectAccessService projectAccess;

    public ProjectController(AuthService auth, ReviewPlatformService platform, AuthorizationService access,
                             ProjectAccessService projectAccess) {
        super(auth);
        this.platform = platform;
        this.access = access;
        this.projectAccess = projectAccess;
    }

    @GetMapping("/projects")
    public List<Project> projects(@RequestHeader("Authorization") String authorization) {
        return projectAccess.listProjects(user(authorization));
    }

    @PostMapping("/projects")
    public Project createProject(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ProjectRequest request) {
        var actor = user(authorization);
        access.require(actor, Permission.PROJECT_WRITE);
        return platform.createProject(request, actor);
    }

    @GetMapping("/drawings")
    public List<Drawing> drawings(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String projectId) {
        return projectAccess.listDrawings(user(authorization), projectId);
    }

    @PostMapping("/drawings")
    public Drawing createDrawing(@RequestHeader("Authorization") String authorization, @Valid @RequestBody DrawingRequest request) {
        var actor = user(authorization);
        access.require(actor, Permission.PROJECT_WRITE);
        return platform.createDrawing(request, actor);
    }

    @GetMapping("/projects/{projectId}/members")
    public List<ProjectMemberView> members(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String projectId
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.PROJECT_MEMBER_MANAGE);
        return projectAccess.listMembers(projectId);
    }

    @PostMapping("/projects/{projectId}/members")
    public ProjectMemberView addMember(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String projectId,
            @Valid @RequestBody AddProjectMemberRequest request
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.PROJECT_MEMBER_MANAGE);
        return projectAccess.addMember(projectId, request.userId(), actor);
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public Map<String, String> removeMember(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String projectId,
            @PathVariable String userId
    ) {
        var actor = user(authorization);
        access.require(actor, Permission.PROJECT_MEMBER_MANAGE);
        projectAccess.removeMember(projectId, userId, actor);
        return Map.of("status", "member_removed");
    }
}
