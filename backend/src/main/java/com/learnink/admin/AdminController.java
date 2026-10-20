package com.learnink.admin;

import com.learnink.admin.AdminDtos.ChangeDatasourceRequest;
import com.learnink.admin.AdminDtos.CreateStudentRequest;
import com.learnink.admin.AdminDtos.DatasourceDto;
import com.learnink.admin.AdminDtos.StudentCredentialsDto;
import com.learnink.admin.AdminDtos.StudentDto;
import com.learnink.auth.AuthDtos.UserDto;
import com.learnink.query.QueryEngine;
import com.learnink.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StudentAccountService studentAccountService;
    private final DatasourceCatalog datasourceCatalog;

    public AdminController(StudentAccountService studentAccountService, DatasourceCatalog datasourceCatalog) {
        this.studentAccountService = studentAccountService;
        this.datasourceCatalog = datasourceCatalog;
    }

    @GetMapping("/students")
    public List<StudentDto> students(@RequestParam(defaultValue = "SQL") QueryEngine engine) {
        return studentAccountService.listStudents(engine);
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentCredentialsDto create(@Valid @RequestBody CreateStudentRequest request) {
        return studentAccountService.createStudent(request.email(), request.datasource());
    }

    @PutMapping("/students/{id}/datasource")
    public StudentDto changeDatasource(@PathVariable Long id, @Valid @RequestBody ChangeDatasourceRequest request) {
        return studentAccountService.changeDatasource(id, request.datasource());
    }

    @PutMapping("/me/datasource")
    public UserDto changeOwnDatasource(@AuthenticationPrincipal AuthenticatedUser teacher,
                                       @Valid @RequestBody ChangeDatasourceRequest request) {
        return studentAccountService.changeOwnDatasource(teacher.id(), request.datasource());
    }

    @GetMapping("/datasources")
    public List<DatasourceDto> datasources() {
        return datasourceCatalog.list();
    }

    @PostMapping("/students/{id}/reset-password")
    public StudentCredentialsDto resetPassword(@PathVariable Long id) {
        return studentAccountService.resetPassword(id);
    }
}
