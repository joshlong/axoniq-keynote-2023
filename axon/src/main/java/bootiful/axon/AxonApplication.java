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
    InitializingBean initializingBean(ObjectMapper objectMapper) {
        return () -> objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT
        );
    }


    @Aggregate
    public static class ConferenceAggregate {

        @AggregateIdentifier
        private String conferenceId;

        @CommandHandler
        public ConferenceAggregate(CreateConferenceCommand conferenceCommand) {
            apply(new ConferenceCreatedEvent(conferenceCommand.id(), conferenceCommand.name()));
            System.out.println("applying!");
        }

        @EventSourcingHandler
        void handle(ConferenceCreatedEvent cce) {
            this.conferenceId = cce.id();
        }
    }

    @Controller
    @RequestMapping("/conferences")
    @ResponseBody
    static class ConferenceController {

        private final CommandGateway commandGateway;
        private final QueryGateway queryGateway;

        ConferenceController(CommandGateway commandGateway, QueryGateway queryGateway) {
            this.commandGateway = commandGateway;
            this.queryGateway = queryGateway;
        }

        @PostMapping
        CompletableFuture<String> create(@RequestParam String conferenceId,
                                         @RequestParam String conferenceName) {
            return this.commandGateway.send(new CreateConferenceCommand(conferenceId, conferenceName));
        }

        @GetMapping
        CompletableFuture<List<Conference>> read() {
            return this.queryGateway
                    .query("allConferences", null,
                            ResponseTypes.multipleInstancesOf(Conference.class));
        }
    }

    @Component
    static class ConferenceProjection {

        private final JdbcClient jdbcClient;

        ConferenceProjection(JdbcClient jdbcClient) {
            this.jdbcClient = jdbcClient;
        }

        @QueryHandler(queryName = "allConferences")
        List<Conference> conferences() {
            var sql = """
                    select * from conference
                    """;
            return this.jdbcClient
                    .sql(sql)
                    .query((rs, rowNum) -> new Conference(
                            rs.getString("id"),
                            rs.getString("name"))
                    )
                    .list();
        }

        @EventHandler
        void handle(ConferenceCreatedEvent conferenceCreatedEvent) {
            var sql = """
                    insert into conference (id, name) values (?,?)
                    """;
            this.jdbcClient
                    .sql(sql)
                    .param(conferenceCreatedEvent.id())
                    .param(conferenceCreatedEvent.name())
                    .update();
        }
    }


    record ConferenceCreatedEvent(String id, String name) {
    }

    record CreateConferenceCommand(String id, String name) {
    }

    record Conference(String id, String name) {
    }
}




