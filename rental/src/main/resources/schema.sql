create table if not exists bike_status
(
    bike_id   varchar(100),
    bike_type varchar(100),
    location  varchar(100),
    renter    varchar(100),
    status    varchar(100),
    primary key (bike_id)
);



create table if not exists TOKENENTRY
(
    segment       integer      not null,
    processorName varchar(255) not null,
    token         text,
    tokenType     varchar(255),
    timestamp     varchar(1000),
    owner         varchar(1000),
    primary key (processorName, segment)
);
