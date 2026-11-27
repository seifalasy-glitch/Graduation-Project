package com.edusync.edusync.backend.dashboard.controller;

import com.edusync.edusync.backend.dashboard.dto.MyEnrollmentResponse;
import com.edusync.edusync.backend.dashboard.dto.TeachingGroupResponse;
import com.edusync.edusync.backend.dashboard.service.MyDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API contract tag "My Dashboard". Roles follow x-roles in openapi.yaml; any other role gets 403. */
@RestController
@RequestMapping("/api/me")
public class MyDashboardController {

    private final MyDashboardService dashboardService;

    public MyDashboardController(MyDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Student home: my courses this term (termId default = current term). */
    @GetMapping("/enrollments")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MyEnrollmentResponse>> myEnrollments(@RequestParam(required = false) Long termId) {
        return ResponseEntity.ok(dashboardService.myEnrollments(termId));
    }

    /** Doctor home: groups I teach this term (termId default = current term). */
    @GetMapping("/teaching-groups")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<TeachingGroupResponse>> myTeachingGroups(@RequestParam(required = false) Long termId) {
        return ResponseEntity.ok(dashboardService.myTeachingGroups(termId));
    }
}
