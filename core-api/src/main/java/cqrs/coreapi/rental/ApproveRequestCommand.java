package cqrs.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ApproveRequestCommand(@TargetAggregateIdentifier String bikeId, String renter) {
}
