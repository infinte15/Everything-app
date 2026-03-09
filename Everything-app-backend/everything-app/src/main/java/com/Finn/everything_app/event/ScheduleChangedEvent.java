package com.Finn.everything_app.event;

import org.springframework.context.ApplicationEvent;

public class ScheduleChangedEvent extends ApplicationEvent {
    private final Long userId;

    public ScheduleChangedEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
