package com.lina.bff.admin.dto;

import jakarta.validation.constraints.Pattern;

public record AdminIngestRequest(
    @Pattern(regexp = "full|delta", message = "mode must be full or delta") String mode) {

  public String normalizedMode() {
    return mode == null || mode.isBlank() ? "full" : mode;
  }
}
