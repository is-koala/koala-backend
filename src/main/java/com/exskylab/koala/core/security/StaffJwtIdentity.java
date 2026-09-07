package com.exskylab.koala.core.security;

import java.util.UUID;

public record StaffJwtIdentity(UUID staffId, int tokenVersion) {
}
