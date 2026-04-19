package com.enrollment.domain.waitlist.dto;

import com.enrollment.domain.waitlist.entity.Waitlist;

import java.time.LocalDateTime;

public record WaitlistResponse(
        Long waitlistId,
        Long classId,
        String classTitle,
        String status,
        LocalDateTime createdAt
) {

    public static WaitlistResponse from(Waitlist waitlist) {
        return new WaitlistResponse(
                waitlist.getId(),
                waitlist.getClassEntity().getId(),
                waitlist.getClassEntity().getTitle(),
                waitlist.getStatus().name(),
                waitlist.getCreatedAt()
        );
    }
}
