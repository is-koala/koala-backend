package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.contact.request.ContactRequestDto;

public interface ContactService {

    void submit(ContactRequestDto request);
}
