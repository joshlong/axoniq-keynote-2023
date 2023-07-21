package cqrs.coreapi.rental;

public record BikeInUseEvent(String bikeId, String renter) {
}
