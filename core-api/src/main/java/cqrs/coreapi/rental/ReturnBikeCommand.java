package cqrs.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ReturnBikeCommand(@TargetAggregateIdentifier String bikeId, String location) {
}
