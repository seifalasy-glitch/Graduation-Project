package com.edusync.edusync.backend.enrollment.controller;

import com.edusync.edusync.backend.enrollment.dto.CreateMembershipRequest;
import com.edusync.edusync.backend.enrollment.dto.EndPeriodRequest;
import com.edusync.edusync.backend.enrollment.dto.GroupMembershipResponse;
import com.edusync.edusync.backend.enrollment.dto.GroupRequest;
import com.edusync.edusync.backend.enrollment.dto.GroupResponse;
import com.edusync.edusync.backend.enrollment.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/offerings/{offeringId}/groups")
    public ResponseEntity<List<GroupResponse>> getGroups(@PathVariable Long offeringId) {
        return ResponseEntity.ok(groupService.getGroups(offeringId));
    }

    @PostMapping("/offerings/{offeringId}/groups")
    public ResponseEntity<GroupResponse> createGroup(
            @PathVariable Long offeringId,
            @RequestBody GroupRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(groupService.createGroup(offeringId, request));
    }

    @PatchMapping("/groups/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable Long groupId,
            @RequestBody GroupRequest request
    ) {
        return ResponseEntity.ok(groupService.updateGroup(groupId, request));
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<List<GroupMembershipResponse>> getMembers(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate at
    ) {
        return ResponseEntity.ok(groupService.getMembers(groupId, at));
    }

    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<GroupMembershipResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateMembershipRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(groupService.addMember(groupId, request));
    }

    @PatchMapping("/group-memberships/{membershipId}")
    public ResponseEntity<GroupMembershipResponse> endMembership(
            @PathVariable Long membershipId,
            @Valid @RequestBody EndPeriodRequest request
    ) {
        return ResponseEntity.ok(groupService.endMembership(membershipId, request));
    }
}
