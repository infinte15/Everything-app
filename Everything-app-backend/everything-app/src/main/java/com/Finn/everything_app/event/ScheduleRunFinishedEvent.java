package com.Finn.everything_app.event;

import org.springframework.context.ApplicationEvent;

/**
 * Ein Scheduler-Lauf ist durch — das Gegenstück zu {@link ScheduleChangedEvent}.
 *
 * <p>{@code ScheduleChangedEvent} heißt "es gibt etwas neu zu planen", dieses Ereignis heißt "es
 * ist neu geplant". Getrennte Ereignisse, weil sie in entgegengesetzte Richtungen laufen: das eine
 * geht von den Fachdiensten in den {@code ScheduleRegenerationCoordinator} hinein, das andere aus
 * dem fertigen Lauf heraus zu allen, die auf das Ergebnis warten.
 *
 * <p>Wird bewusst NICHT vom Scheduler selbst veröffentlicht, sondern von
 * {@code LastScheduleRunStore.record(...)} — dem einen Punkt, an dem ein Ergebnis entsteht.
 */
public class ScheduleRunFinishedEvent extends ApplicationEvent {
    private final Long userId;

    public ScheduleRunFinishedEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
