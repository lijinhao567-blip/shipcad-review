package com.shipcad.review.api;

import com.shipcad.review.domain.KnowledgeClause;
import com.shipcad.review.repo.KnowledgeClauseRepository;
import com.shipcad.review.service.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-clauses")
public class KnowledgeController extends BaseController {
    private final KnowledgeClauseRepository clauses;

    public KnowledgeController(AuthService auth, KnowledgeClauseRepository clauses) {
        super(auth);
        this.clauses = clauses;
    }

    @GetMapping
    public List<KnowledgeClause> clauses(@RequestHeader("Authorization") String authorization) {
        user(authorization);
        return clauses.findAll();
    }
}
