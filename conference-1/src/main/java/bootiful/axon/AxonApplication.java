package bootiful.axon;

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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@SpringBootApplication
public class AxonApplication {

    public static void main(String[] args) {
        SpringApplication.run(AxonApplication.class, args);
    }

    @Bean
    InitializingBean onStartup(ObjectMapper objectMapper) {
        return () -> objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }


    @Component
    static class ConferenceProjection {

        private final JdbcClient jdbcClient;

        ConferenceProjection(JdbcClient jdbcClient) {
            this.jdbcClient = jdbcClient;
        }

        @EventHandler
        void handle(Messages.ConferenceCreatedEvent conferenceCreatedEvent) {
            this.jdbcClient.sql("insert into conference (id, name) values (?,?)")
                    .param(conferenceCreatedEvent.conferenceId())
                    .param(conferenceCreatedEvent.conferenceName())
                    .update();
        }

        @QueryHandler(queryName = "allConferences")
        List<Conference> conferences() {
            return this.jdbcClient
                    .sql("select * from conference")
                    .query((rs, rowNum) -> new Conference(rs.getString("id"),
                            rs.getString("name")))
                    .list();
        }

    }

    @Aggregate
    public static class ConferenceAggregate {

        @AggregateIdentifier
        private String conferenceId;

        @CommandHandler
        public ConferenceAggregate(Messages.CreateConferenceCommand conferenceCommand) {
            Assert.notNull(conferenceCommand.conferenceName(), "the conference name must be non-null");

            apply(new Messages.ConferenceCreatedEvent(conferenceCommand.conferenceId(),
                    conferenceCommand.conferenceName()));
        }

        @EventSourcingHandler
        void handle(Messages.ConferenceCreatedEvent cae) {
            this.conferenceId = cae.conferenceId();
        }
    }


    @Controller
    @RequestMapping("/")
    @ResponseBody
    static class ConferenceController {

        private final CommandGateway commandGateway;

        private final QueryGateway queryGateway;

        ConferenceController(CommandGateway commandGateway, QueryGateway queryGateway) {
            this.commandGateway = commandGateway;
            this.queryGateway = queryGateway;
        }

        @GetMapping
        CompletableFuture<List<Conference>> conferences() {
            return this.queryGateway.query(
                    "allConferences", null, ResponseTypes.multipleInstancesOf(Conference.class));
        }

        @PostMapping
        CompletableFuture<String> createConference(@RequestParam String conferenceId, @RequestParam String conferenceName) {
            return commandGateway.send(
                    new Messages.CreateConferenceCommand(conferenceId, conferenceName));
        }

    }


    static class Messages {

        record ConferenceCreatedEvent(String conferenceId, String conferenceName) {
        }

        record CreateConferenceCommand(String conferenceId, String conferenceName) {
        }
    }


    record Conference(String conferenceId, String conferenceName) {
    }
}
