package cqrs.coreapi.rental;

public record BikeStatus(
        @org.springframework.data.annotation.Id String bikeId,
        String location,
        String renter,
        RentalStatus status) {

    public String description() {
        return switch (status) {
            case RENTED -> String.format("Bike %s was rented by %s in %s", bikeId, renter, location);
            case AVAILABLE -> String.format("Bike %s is available for rental in %s.", bikeId, location);
            case REQUESTED -> String.format("Bike %s is requested by %s in %s", bikeId, renter, location);

        };
    }

    public BikeStatus rentedBy(String renter) {
        return new BikeStatus(this.bikeId,
                this.location, renter, RentalStatus.RENTED);
    }


    public BikeStatus requestedBy(String renter) {
        return new BikeStatus(this.bikeId,
                this.location, renter, RentalStatus.REQUESTED);
    }

    public BikeStatus returnedAt(String location) {
        return new BikeStatus(this.bikeId, location, this.renter, RentalStatus.AVAILABLE);
    }

}


