create table if not exists CONFERENCE_SPEAKER
(
    id varchar(64) not null ,
    conference_name varchar(100),
    speaker_name  varchar(100),
    primary key (id)
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
