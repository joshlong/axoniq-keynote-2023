package cqrs.rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@SpringBootApplication
public class ConferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConferenceApplication.class, args);
    }

    @Bean
    ApplicationRunner onStartup(ObjectMapper objectMapper) {
        return (args) -> objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                                                            ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }
}


@Repository
class ConferenceSpeakerRepository {

    private final JdbcClient jdbcClient;

    ConferenceSpeakerRepository(JdbcClient jc) {
        this.jdbcClient = jc;
    }

    void save(ConferenceSpeaker speaker) {
        this.jdbcClient
                .sql("insert into CONFERENCE_SPEAKER ( id, conference_name, speaker_name) values(?,?,?)")
                .param(speaker.id())
                .param(speaker.conferenceName())
                .param(speaker.speakerName())
                .update();
    }

    List<ConferenceSpeaker> findAll() {
        return this.jdbcClient
                .sql("select * from CONFERENCE_SPEAKER")
                .query((ResultSet rs, int rowNum) -> new ConferenceSpeaker(rs.getString("id"),
                                                                           rs.getString("conference_name"),
                                                                           rs.getString("speaker_name")))
                .list();
    }
}


@Aggregate
class ConferenceAggregate {

    @AggregateIdentifier
    private String conferenceId;
    private List<String> speakers;
    private String conferenceName;

    public ConferenceAggregate() {
    }

    @CommandHandler
    public ConferenceAggregate(Messages.AnnounceConferenceCommand command) {
        apply(new Messages.ConferenceAnnouncedEvent(command.conferenceId(), command.conferenceName()));
    }

    @CommandHandler
    public void handle(Messages.AnnounceSpeakerCommand command) {
        if (speakers.contains(command.speaker())) {
            throw new IllegalStateException("Speaker already announced");
        }
        apply(new Messages.SpeakerAnnouncedEvent(command.conferenceId(), conferenceName, command.speaker()));
    }

    @EventSourcingHandler
    protected void handle(Messages.ConferenceAnnouncedEvent event) {
        this.conferenceId = event.conferenceId();
        this.conferenceName = event.conferenceName();
        this.speakers = new ArrayList<>();
    }

    @EventSourcingHandler
    protected void handle(Messages.SpeakerAnnouncedEvent event) {
        this.speakers.add(event.speaker());
    }
}

@Component
class SpeakerProjection {

    private final ConferenceSpeakerRepository repository;


    SpeakerProjection(ConferenceSpeakerRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void on(Messages.SpeakerAnnouncedEvent event) {
        repository.save(new ConferenceSpeaker(UUID.randomUUID().toString(), event.conferenceName(), event.speaker()));
    }

    @QueryHandler(queryName = "allSpeakers")
    public List<ConferenceSpeaker> allSpeakers() {
        return repository.findAll();
    }
}

@RestController
@RequestMapping("/")
class ConferenceController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    ConferenceController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping
    CompletableFuture<String> announceConference(@RequestParam("conferenceId") String conferenceId,
                                                 @RequestParam("conferenceName") String conferenceName) {
        return commandGateway.send(new Messages.AnnounceConferenceCommand(conferenceId, conferenceName));
    }

    @PostMapping("/{conferenceId}")
    CompletableFuture<Void> announceSpeaker(@PathVariable("conferenceId") String conferenceId,
                                            @RequestParam("speakerName") String speakerName) {
        return commandGateway.send(new Messages.AnnounceSpeakerCommand(conferenceId, speakerName));
    }

    @GetMapping()
    CompletableFuture<List<ConferenceSpeaker>> findAll() {
        return queryGateway.query("allSpeakers", null, ResponseTypes.multipleInstancesOf(ConferenceSpeaker.class));
    }
}