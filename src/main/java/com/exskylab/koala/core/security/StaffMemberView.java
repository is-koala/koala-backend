package com.exskylab.koala.core.security;

import com.exskylab.koala.entities.StaffMember;

import java.util.List;

public record StaffMemberView(StaffMember member, List<String> roles) {
}
