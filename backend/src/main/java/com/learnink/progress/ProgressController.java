package com.learnink.progress;

import com.learnink.progress.ProgressDtos.ProgressDto;
import com.learnink.query.QueryEngine;
import com.learnink.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping
    public ProgressDto progress(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(defaultValue = "SQL") QueryEngine engine) {
        return progressService.progress(user, engine);
    }
}
