package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.myWork.response.WorkFeedDto;

public interface MyWorkService {

    WorkFeedDto getMyWork(boolean asEmployer, int limit);
}
