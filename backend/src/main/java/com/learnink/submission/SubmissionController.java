package com.learnink.submission;

import com.learnink.security.AuthenticatedUser;
import com.learnink.submission.SubmissionDtos.SubmissionHistoryDto;
import com.learnink.submission.SubmissionDtos.SubmissionRequest;
import com.learnink.submission.SubmissionDtos.SubmissionResultDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exercises/{exerciseId}/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public SubmissionResultDto submit(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Long exerciseId,
                                      @Valid @RequestBody SubmissionRequest request) {
        return submissionService.submit(user, exerciseId, request.query());
    }

    @GetMapping
    public List<SubmissionHistoryDto> history(@AuthenticationPrincipal AuthenticatedUser user,
                                              @PathVariable Long exerciseId) {
        return submissionService.history(user, exerciseId);
    }
}
