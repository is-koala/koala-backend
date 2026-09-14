package com.exskylab.koala.core.events;

import java.util.UUID;

public record CompanyContactRemovedEvent(UUID companyId, UUID userId) {
}
