package com.learnink.exercise;

import com.learnink.exercise.ExerciseDtos.ExerciseDetailDto;
import com.learnink.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{id}")
    public ExerciseDetailDto detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return exerciseService.detail(user, id);
    }
}
