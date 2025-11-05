package io.redlink.more.data.event;

import io.redlink.more.data.model.RoutingInfo;
import org.springframework.context.ApplicationEvent;

public class DeregistrationSpringEvent extends ApplicationEvent {
    private final RoutingInfo routingInfo;

    public DeregistrationSpringEvent(Object source, RoutingInfo routingInfo) {
        super(source);
        this.routingInfo = routingInfo;
    }

    public RoutingInfo getRoutingInfo() {
        return routingInfo;
    }
}
