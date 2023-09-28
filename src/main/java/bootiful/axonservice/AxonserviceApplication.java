package bootiful.axonservice;

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
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.IntStream;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@SpringBootApplication
public class AxonserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AxonserviceApplication.class, args);
    }


    String sleep(int index) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return index == 0 ? Thread.currentThread().toString() : "";
    }

    @Bean
    ApplicationRunner applicationRunner() {
        return a -> {

            var observed = new ConcurrentSkipListSet<String>();

            var threads = IntStream
                    .range(0, 100)
                    .mapToObj(index -> Thread.ofVirtual()
                            .unstarted(() -> {

                                observed.add(sleep(index));
                                observed.add(sleep(index));
                                observed.add(sleep(index));


                            }))
                    .toList();

            for (var t : threads) t.start();

            for (var t : threads) t.join();

            System.out.println(observed);

        };
    }


    @Bean
    InitializingBean initializingBean(ObjectMapper objectMapper) {
        return () -> objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }

    @Aggregate
    public static class ConferenceAggregate {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public ConferenceAggregate(CreateConferenceCommand cc) {
            apply(new ConferenceCreatedEvent(cc.id(), cc.name()));
        }

        @EventSourcingHandler
        void handle(ConferenceCreatedEvent cce) {
            this.id = cce.id();
        }
    }

    @Component
    static class ConferenceProjection {

        private final JdbcClient jdbc;

        ConferenceProjection(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @EventHandler
        void write(ConferenceCreatedEvent cce) {
            var sql = """
                    insert into conference (id, name) values(?,?)
                    """;
            this.jdbc
                    .sql(sql)
                    .param(cce.id())
                    .param(cce.name())
                    .update();
        }

        @QueryHandler(queryName = "allConferences")
        List<Conference> read() {
            var sql = """
                    select * from conference 
                    """;
            return this.jdbc.sql(sql)
                    .query((rs, i) -> new Conference(
                            rs.getString("id"),
                            rs.getString("name")))
                    .list();
        }

    }

    record ConferenceCreatedEvent(String id, String name) {
    }

    @Controller
    @RequestMapping("/conferences")
    @ResponseBody
    static class ConferenceController {

        private final CommandGateway commands;
        private final QueryGateway queries;

        ConferenceController(CommandGateway commands, QueryGateway queries) {
            this.commands = commands;
            this.queries = queries;
        }

        @GetMapping
        CompletableFuture<List<Conference>> read() {
            return this.queries.query("allConferences", null,
                    ResponseTypes.multipleInstancesOf(Conference.class));
        }

        @PostMapping
        CompletableFuture<String> write(@RequestParam String id, @RequestParam String name) {
            return this.commands.send(new CreateConferenceCommand(id, name));
        }
    }

    record Conference(String id, String name) {
    }

    // look mom, no Lombok!
    record CreateConferenceCommand(String id, String name) {
    }

}


