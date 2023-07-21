package cqrs.coreapi.rental;

public record BikeReturnedEvent(String bikeId, String location) {
}
