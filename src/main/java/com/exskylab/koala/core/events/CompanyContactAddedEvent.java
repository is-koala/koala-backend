package com.exskylab.koala.core.events;

import java.util.UUID;

public record CompanyContactAddedEvent(UUID companyId, UUID userId) {
}
