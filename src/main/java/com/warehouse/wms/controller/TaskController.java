package com.warehouse.wms.controller;

import com.warehouse.wms.dto.TaskConfirmDTO;
import com.warehouse.wms.dto.TaskDTO;
import com.warehouse.wms.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Management", description = "APIs for claiming and confirming warehouse tasks")
public class TaskController {

    // There is no authentication yet, so the caller names the user in a header
    private static final String USER_HEADER = "X-User-Id";

    private final TaskService taskService;

    @PostMapping("/next")
    @Operation(summary = "Claim next task", description = "Claim the highest-priority READY pick task, optionally in one zone; 204 if there is none")
    public ResponseEntity<TaskDTO> claimNext(@RequestParam(required = false) Long zoneId,
                                             @RequestHeader(value = USER_HEADER, required = false) String user) {
        return taskService.claimNext(zoneId, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID", description = "Retrieve a task")
    public ResponseEntity<TaskDTO> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm task", description = "Confirm a claimed pick with the scanned location and picked quantity; less than the task quantity is a short pick")
    public ResponseEntity<TaskDTO> confirm(@PathVariable Long id,
                                           @Valid @RequestBody TaskConfirmDTO request,
                                           @RequestHeader(value = USER_HEADER, required = false) String user,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(taskService.confirm(id, request, user, idempotencyKey));
    }
}
