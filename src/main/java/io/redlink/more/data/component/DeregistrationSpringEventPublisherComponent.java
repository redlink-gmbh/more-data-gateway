package io.redlink.more.data.component;

import io.redlink.more.data.event.DeregistrationSpringEvent;
import io.redlink.more.data.model.RoutingInfo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DeregistrationSpringEventPublisherComponent {
    private final ApplicationEventPublisher eventPublisher;

    public DeregistrationSpringEventPublisherComponent(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishParticipantDeregistrationEvent(RoutingInfo routingInfo) {
        eventPublisher.publishEvent(new DeregistrationSpringEvent(this, routingInfo));
    }

}
