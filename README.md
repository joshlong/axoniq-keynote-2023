# AxonIQ keynote 2023


## axon has come a long way and so has our friendship 
- [infoq article](https://www.infoq.com/articles/cqrs_with_axon_framework/)  
- 0.4 release of axon! 
- MAR 24, 2010 
- 13+ years ago! 


## start.spring.io
- using the new boot 3.2 M3
- using java 21
- using maven (whomp whomp)
- web, jdbc, postgres, devtools, testcontainers, graalvm, actuator 

## code with me and intellij 
- this is another thing allard and i couldn't have conceived of 13+ years ago! 
- filler (Allard to tell a story about pair programming with Josh)

## add axon 

```xml

<dependency>
    <groupId>org.axonframework.extensions.spring-aot</groupId>
    <artifactId>axon-spring-aot</artifactId>
    <version>4.9.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-spring-boot-starter</artifactId>
    <version>4.9.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-micrometer</artifactId>
    <version>4.9.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-test</artifactId>
    <scope>test</scope>
    <version>4.9.0-SNAPSHOT</version>
</dependency>
```

and (just to be able to resolve the SNAPSHOT dependencies)

```xml
      <repository>
        <id>sonatype</id>
        <name>Sonatype</name>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <releases>
            <enabled>false</enabled>
        </releases>
    </repository>
```        


## git clone run lifestyle
- check out the new testcontainers support 
- wanna use this with axon server 
- easy! 


- we need something to help us keep track of our messages and to help distribute the load
- we need axon server
- allard tells us about the Axon Server
- add the AxonServerContainer bean 

```java

    @Bean
    @ServiceConnection
    AxonServerContainer axonServerContainer() {
        return new AxonServerContainer(DockerImageName.parse("axoniq/axonserver:latest-dev"));
    }
```

## autoconfig and axon go together like coffee and stroopwafel
- axon's always had good support for spring 
- history of the axon autoconfiguration 
- axon is easier than ever AND more powerful than ever 

## devtools 
- explain how devtools works
- explain `@RestartScope`
- make sure module/project settings show java 21
- THEN: start the app: 20s first, then 0.03

## build the conference query/command/handler 
- before testing, make sure to specify `axon.serializer.general=jackson`
- add
```java
    @Bean
    InitializingBean configureJackson(ObjectMapper objectMapper) {
        return () -> objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }
```
 
- then run it and hit `curl http://localhost:8080/?conferenceId=1&conferenceName=AxonIQCon`


## working code thus far

- ```
@Aggregate
public static class ConferenceAggregate {

    @AggregateIdentifier
    private String conferenceId;

    public ConferenceAggregate() {
    }

    @EventSourcingHandler
    void handle(Messages.ConferenceAnnouncedEvent cae) {
        this.conferenceId = cae.conferenceId();
    }

    @CommandHandler
    public ConferenceAggregate(Messages.CreateConferenceCommand conferenceCommand) {
        // goes to axon server
        apply(new Messages.ConferenceAnnouncedEvent(conferenceCommand.conferenceId(),
                conferenceCommand.conferenceName()));
    }
}


@Controller
@RequestMapping("/")
@ResponseBody
class ConferenceController {

    private final CommandGateway commandGateway;

    ConferenceController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
    }

    @PostMapping
    CompletableFuture<String> createConference(@RequestParam String conferenceId, @RequestParam String conferenceName) {
        return commandGateway.send(
                new Messages.CreateConferenceCommand(conferenceId, conferenceName));
    }

}


class Messages {

    record ConferenceAnnouncedEvent(String conferenceId, String conferenceName) {
    }

    record CreateConferenceCommand(String conferenceId, String conferenceName) {
    }
}

```

- Great!  able to write, (via commands), but we need to read
- send the query with the querygateway

```java 

    @GetMapping
    CompletableFuture<List<Conference>> conferences() {
        return this.queryGateway.query(
                "allConferences", null, ResponseTypes.multipleInstancesOf(Conference.class));
    }
```

- this issues a query to the "projection", whose job it is to keep an optimized projection of the data for particular queries. we'll resolve ours using jdbc.

```java 

@Component
class ConferenceProjection {

    private final JdbcClient jdbcClient;

    ConferenceProjection(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @EventHandler
    void handle(Messages.ConferenceCreatedEvent conferenceCreatedEvent) {
        this.jdbcClient.sql("insert into CONFERENCE (id, name) values (?,?)")
                .param(conferenceCreatedEvent.conferenceId())
                .param(conferenceCreatedEvent.conferenceName())
                .update();
    }

    @QueryHandler(queryName = "allConferences")
    List<Conference> conferences() {
        return this.jdbcClient
                .sql("select * from CONFERENCE")
                .query((rs, rowNum) -> new Conference(rs.getString("id"),
                        rs.getString("name")))
                .list();
    }

}
```

We need some SQL schema: 

```sql 

create table if not exists conference
(
    id   varchar(64) not null primary key,
    name text        not null
);

create table if not exists tokenentry
(
    segment       integer      not null,
    processorName varchar(255) not null,
    token         bytea,
    tokenType     varchar(255),
    timestamp     varchar(1000),
    owner         varchar(1000),
    primary key (processorName, segment)
);
```


## native 

``` ./mvnw native:compile -Pnative -DskipTests ```
