package cqrs.rental;

import cqrs.coreapi.rental.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.jdbc.GenericTokenTableFactory;
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@EntityScan(basePackageClasses = {
        BikeStatus.class
})
@SpringBootApplication
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
    }

}

interface BikeStatusRepository extends ListCrudRepository<BikeStatus, String> {
}

@Component
class BikeStatusProjection {

    private final BikeStatusRepository bikeStatusRepository;
    private final QueryUpdateEmitter updateEmitter;

    public BikeStatusProjection(BikeStatusRepository bikeStatusRepository, QueryUpdateEmitter updateEmitter) {
        this.bikeStatusRepository = bikeStatusRepository;
        this.updateEmitter = updateEmitter;
    }

    @EventHandler
    public void on(BikeRegisteredEvent event) {
        var bikeStatus = new BikeStatus(event.bikeId(), event.bikeType(), event.location(), RentalStatus.AVAILABLE);
        bikeStatusRepository.save(bikeStatus);
        updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bikeStatus);
    }

    @EventHandler
    public void on(BikeRequestedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                .map(bs -> bs.requestedBy(event.renter()))
                .ifPresent(bs -> {
                    bikeStatusRepository.save(bs);
                    updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                    updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                });
    }

    @EventHandler
    public void on(BikeInUseEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                .map(bs -> bs.rentedBy(event.renter()))
                .ifPresent(bs -> {
                    bikeStatusRepository.save(bs);
                    updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                    updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                });
    }

    @EventHandler
    public void on(BikeReturnedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                .map(bs -> bs.returnedAt(event.location()))
                .ifPresent(bs -> {
                    bikeStatusRepository.save(bs);
                    updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                    updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                });

    }

    @EventHandler
    public void on(RequestRejectedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                .map(bs -> bs.returnedAt(bs.location()))
                .ifPresent(bs -> {
                    bikeStatusRepository.save(bs);
                    updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                    updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                });
    }

    @QueryHandler(queryName = "findAll")
    public Iterable<BikeStatus> findAll() {
        return bikeStatusRepository.findAll();
    }

/*
    @QueryHandler(queryName = "findAvailable")
    public Iterable<BikeStatus> findAvailable(String bikeType) {
        return bikeStatusRepository.findAllByBikeTypeAndStatus(bikeType, RentalStatus.AVAILABLE);
    }
*/

    @QueryHandler(queryName = "findOne")
    public BikeStatus findOne(String bikeId) {
        return bikeStatusRepository.findById(bikeId).orElse(null);
    }
}


@Aggregate
class Bike {

    @AggregateIdentifier
    private String bikeId;

    private boolean isAvailable;
    private String reservedBy;
    private boolean reservationConfirmed;

    public Bike() {
    }

    @CommandHandler
    public Bike(RegisterBikeCommand command) {
        apply(new BikeRegisteredEvent(command.bikeId(), command.bikeType(), command.location()));
    }

    @CommandHandler
    public String handle(RequestBikeCommand command) {
        if (!this.isAvailable) {
            throw new IllegalStateException("Bike is already rented");
        }
        String rentalReference = UUID.randomUUID().toString();
        apply(new BikeRequestedEvent(command.bikeId(), command.renter(), rentalReference));

        return rentalReference;
    }

    @CommandHandler
    public void handle(ApproveRequestCommand command) {
        if (!Objects.equals(reservedBy, command.renter())
            || reservationConfirmed) {
            return;
        }
        apply(new BikeInUseEvent(command.bikeId(), command.renter()));
    }

    @CommandHandler
    public void handle(RejectRequestCommand command) {
        if (!Objects.equals(reservedBy, command.renter())
            || reservationConfirmed) {
            return;
        }
        apply(new RequestRejectedEvent(command.bikeId()));
    }

    @CommandHandler
    public void handle(ReturnBikeCommand command) {
        if (this.isAvailable) {
            throw new IllegalStateException("Bike was already returned");
        }
        apply(new BikeReturnedEvent(command.bikeId(), command.location()));
    }

    @EventSourcingHandler
    protected void handle(BikeRegisteredEvent event) {
        this.bikeId = event.bikeId();
        this.isAvailable = true;
    }

    @EventSourcingHandler
    protected void handle(BikeReturnedEvent event) {
        this.isAvailable = true;
        this.reservationConfirmed = false;
        this.reservedBy = null;
    }

    @EventSourcingHandler
    protected void handle(BikeRequestedEvent event) {
        this.reservedBy = event.renter();
        this.reservationConfirmed = false;
        this.isAvailable = false;
    }

    @EventSourcingHandler
    protected void handle(RequestRejectedEvent event) {
        this.reservedBy = null;
        this.reservationConfirmed = false;
        this.isAvailable = true;
    }

    @EventSourcingHandler
    protected void on(BikeInUseEvent event) {
        this.isAvailable = false;
        this.reservationConfirmed = true;
    }
}


@RestController
@RequestMapping("/")
class RentalController {

    //    private static final List<String> RENTERS = List.of("Allard", "Steven", "Josh", "David", "Marc", "Sara", "Milan", "Jeroen", "Marina", "Jeannot");
    private static final List<String> LOCATIONS = List.of("Amsterdam", "Paris", "Vilnius", "Barcelona", "London", "New York", "Toronto", "Berlin", "Milan", "Rome", "Belgrade");
    public static final String FIND_ALL_QUERY = "findAll";

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    RentalController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    CompletableFuture<Void> generateBikes(
            @RequestParam("bikes") int bikeCount, @RequestParam(value = "bikeType") String bikeType) {
        CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
        for (var i = 0; i < bikeCount; i++) {
            all = CompletableFuture.allOf(all,
                    commandGateway.send(new RegisterBikeCommand(UUID.randomUUID().toString(), bikeType, randomLocation())));
        }
        return all;
    }

    @GetMapping("/bikes")
    CompletableFuture<List<BikeStatus>> findAll() {
        return queryGateway.query(FIND_ALL_QUERY, null, ResponseTypes.multipleInstancesOf(BikeStatus.class));
    }

//    private String randomRenter() {
//        return RENTERS.get(ThreadLocalRandom.current().nextInt(RENTERS.size()));
//    }

    private String randomLocation() {
        return LOCATIONS.get(ThreadLocalRandom.current().nextInt(LOCATIONS.size()));
    }
/*

    @GetMapping("/bikeUpdates")
    public Flux<ServerSentEvent<String>> subscribeToAllUpdates() {
        SubscriptionQueryResult<List<BikeStatus>, BikeStatus> subscriptionQueryResult = queryGateway.subscriptionQuery(FIND_ALL_QUERY, null, ResponseTypes.multipleInstancesOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQueryResult.initialResult()
                .flatMapMany(Flux::fromIterable)
                .concatWith(subscriptionQueryResult.updates())
                .doFinally(s -> subscriptionQueryResult.close())
                .map(BikeStatus::description)
                .map(description -> ServerSentEvent.builder(description).build());
    }

    @GetMapping("/bikeUpdates/{bikeId}")
    public Flux<ServerSentEvent<String>> subscribeToBikeUpdates(@PathVariable("bikeId") String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> subscriptionQueryResult = queryGateway.subscriptionQuery(FIND_ONE_QUERY, bikeId, BikeStatus.class, BikeStatus.class);
        return subscriptionQueryResult.initialResult()
                .concatWith(subscriptionQueryResult.updates())
                .doFinally(s -> subscriptionQueryResult.close())
                .map(BikeStatus::description)
                .map(description -> ServerSentEvent.builder(description).build());
    }

    @PostMapping("/requestBike")
    public CompletableFuture<String> requestBike(@RequestParam("bikeId") String bikeId, @RequestParam(value = "renter", required = false) String renter) {
        return commandGateway.send(new RequestBikeCommand(bikeId, renter != null ? renter : randomRenter()));
    }

    @PostMapping("/returnBike")
    public CompletableFuture<String> returnBike(@RequestParam("bikeId") String bikeId) {
        return commandGateway.send(new ReturnBikeCommand(bikeId, randomLocation()));
    }

    @GetMapping("findPayment")
    public Mono<String> getPaymentId(@RequestParam("reference") String paymentRef) {
        SubscriptionQueryResult<String, String> queryResult = queryGateway.subscriptionQuery("getPaymentId", paymentRef, String.class, String.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                .filter(Objects::nonNull)
                .next();

    }

    @GetMapping("pendingPayments")
    public CompletableFuture<PaymentStatus> getPendingPayments() {
        return queryGateway.query("getAllPayments", PaymentStatus.Status.PENDING, PaymentStatus.class);
    }

    @PostMapping("acceptPayment")
    public CompletableFuture<Void> acceptPayment(@RequestParam("id") String paymentId) {
        return commandGateway.send(new ConfirmPaymentCommand(paymentId));
    }


    @GetMapping(value = "watch", produces = "text/event-stream")
    public Flux<String> watchAll() {
        SubscriptionQueryResult<List<BikeStatus>, BikeStatus> subscriptionQuery = queryGateway.subscriptionQuery(FIND_ALL_QUERY, null, ResponseTypes.multipleInstancesOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQuery.initialResult()
                .flatMapMany(Flux::fromIterable)
                .concatWith(subscriptionQuery.updates())
                .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }

    @GetMapping(value = "watch/{bikeId}", produces = "text/event-stream")
    public Flux<String> watchBike(@PathVariable("bikeId") String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> subscriptionQuery = queryGateway.subscriptionQuery(FIND_ONE_QUERY, bikeId, ResponseTypes.instanceOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQuery.initialResult()
                .concatWith(subscriptionQuery.updates())
                .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }


    @PostMapping(value = "/generateRentals")
    public Flux<String> generateData(@RequestParam(value = "bikeType") String bikeType,
                                     @RequestParam("loops") int loops,
                                     @RequestParam(value = "concurrency", defaultValue = "1") int concurrency) {

        return Flux.range(0, loops)
                .flatMap(j -> executeRentalCycle(bikeType, randomRenter()).map(r -> "OK - Rented, Payed and Returned\n")
                                .onErrorResume(e -> Mono.just("Not ok: " + e.getMessage() + "\n")),
                        concurrency);
    }

    @GetMapping("/bikes/{bikeId}")
    public CompletableFuture<BikeStatus> findStatus(@PathVariable("bikeId") String bikeId) {
        return queryGateway.query(FIND_ONE_QUERY, bikeId, BikeStatus.class);
    }

    private Mono<String> executeRentalCycle(String bikeType, String renter) {
        CompletableFuture<String> result = selectRandomAvailableBike(bikeType)
                .thenCompose(bikeId -> commandGateway.send(new RequestBikeCommand(bikeId, renter))
                        .thenCompose(paymentRef -> executePayment(bikeId, (String) paymentRef))
                        .thenCompose(r -> whenBikeUnlocked(bikeId))
                        .thenCompose(r -> commandGateway.send(new ReturnBikeCommand(bikeId, randomLocation())))
                        .thenApply(r -> bikeId));
        return Mono.fromFuture(result);
    }

    private CompletableFuture<String> selectRandomAvailableBike(String bikeType) {
        return queryGateway.query("findAvailable", bikeType, ResponseTypes.multipleInstancesOf(BikeStatus.class))
                .thenApply(this::pickRandom)
                .thenApply(BikeStatus::getBikeId);
    }

    private <T> T pickRandom(List<T> source) {
        return source.get(ThreadLocalRandom.current().nextInt(source.size()));
    }

    private CompletableFuture<String> whenBikeUnlocked(String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> queryResult = queryGateway.subscriptionQuery(FIND_ONE_QUERY, bikeId, BikeStatus.class, BikeStatus.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                .any(status -> status.getStatus() == RentalStatus.RENTED)
                .map(s -> bikeId)
                .doOnNext(n -> queryResult.close())
                .toFuture();
    }

    private CompletableFuture<String> executePayment(String bikeId, String paymentRef) {
        SubscriptionQueryResult<String, String> queryResult = queryGateway.subscriptionQuery("getPaymentId", paymentRef, String.class, String.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                .filter(Objects::nonNull)
                .doOnNext(n -> queryResult.close())
                .next()
                .flatMap(paymentId -> Mono.fromFuture(commandGateway.send(new ConfirmPaymentCommand(paymentId))))
                .map(o -> bikeId)
                .toFuture();
    }


*/

}