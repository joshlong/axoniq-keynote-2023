create table if not exists conference
(
    id   varchar(64) not null primary key,
    name text        not null
);
delete from conference;


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
delete from tokenentry;