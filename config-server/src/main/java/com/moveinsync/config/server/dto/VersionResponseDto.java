package com.moveinsync.config.server.dto;

import java.util.UUID;

public record VersionResponseDto(UUID epoch, long version) {}

