package com.shipcad.review.api;

import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.Project;
import com.shipcad.review.dto.ApiDtos.DrawingRequest;
import com.shipcad.review.dto.ApiDtos.ProjectRequest;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ReviewPlatformService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProjectController extends BaseController {
    private final ProjectRepository projects;
    private final DrawingRepository drawings;
    private final ReviewPlatformService platform;

    public ProjectController(AuthService auth, ProjectRepository projects, DrawingRepository drawings, ReviewPlatformService platform) {
        super(auth);
        this.projects = projects;
        this.drawings = drawings;
        this.platform = platform;
    }

    @GetMapping("/projects")
    public List<Project> projects(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        return projects.findAll();
    }

    @PostMapping("/projects")
    public Project createProject(@RequestHeader("Authorization") String authorization, @Valid @RequestBody ProjectRequest request) {
        return platform.createProject(request, user(authorization));
    }

    @GetMapping("/drawings")
    public List<Drawing> drawings(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String projectId) {
        user(authorization);
        return projectId == null || projectId.isBlank() ? drawings.findAll() : drawings.findByProjectId(projectId);
    }

    @PostMapping("/drawings")
    public Drawing createDrawing(@RequestHeader("Authorization") String authorization, @Valid @RequestBody DrawingRequest request) {
        return platform.createDrawing(request, user(authorization));
    }
}
