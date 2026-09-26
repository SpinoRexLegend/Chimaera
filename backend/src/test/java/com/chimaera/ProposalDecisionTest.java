package com.chimaera;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ProposalDecisionTest {
    @Test void draftCannotBeAnswered() {
        assertThrows(ResponseStatusException.class, () -> new Proposal().respond(ProposalStatus.ACCEPTED));
    }
    @Test void acceptanceIsIdempotentAndCannotBeReversedOrResent() {
        var proposal = new Proposal(); proposal.send(); proposal.respond(ProposalStatus.ACCEPTED);
        var timestamp = proposal.respondedAt();
        proposal.respond(ProposalStatus.ACCEPTED);
        assertEquals(timestamp, proposal.respondedAt());
        assertEquals(ProposalStatus.ACCEPTED, proposal.status());
        assertThrows(ResponseStatusException.class, () -> proposal.respond(ProposalStatus.DECLINED));
        assertThrows(ResponseStatusException.class, proposal::send);
    }
    @Test void declineIsPersistableAndInvalidDecisionsFail() {
        var proposal = new Proposal(); proposal.send();
        assertThrows(ResponseStatusException.class, () -> proposal.respond(null));
        proposal.respond(ProposalStatus.DECLINED);
        assertEquals(ProposalStatus.DECLINED, proposal.status());
        assertNotNull(proposal.respondedAt());
    }
}
