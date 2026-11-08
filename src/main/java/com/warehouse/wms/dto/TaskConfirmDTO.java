package com.warehouse.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the picker scanned and how many units they picked; fewer than the task asks for is a short pick. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfirmDTO {

    private String locationCode;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer qty;
}
