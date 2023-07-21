package cqrs.coreapi.rental;

public record BikeRequestedEvent(String bikeId, String renter, String rentalReference) {
}
