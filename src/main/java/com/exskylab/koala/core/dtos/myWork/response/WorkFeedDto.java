package com.exskylab.koala.core.dtos.myWork.response;

import java.util.List;

public record WorkFeedDto(List<WorkItemDto> items, int applicationCount, int assignmentCount) {
}
