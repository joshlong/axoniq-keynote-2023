package cqrs.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class Messages {

    public record AnnounceConferenceCommand(String conferenceId, String conferenceName) {

    }

    public record AnnounceSpeakerCommand(@TargetAggregateIdentifier String conferenceId, String speaker) {

    }

    public record ConferenceAnnouncedEvent(String conferenceId, String conferenceName) {
    }

    public record SpeakerAnnouncedEvent(String conferenceId, String conferenceName, String speaker) {

    }
}
