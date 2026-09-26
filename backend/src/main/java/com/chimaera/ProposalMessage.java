package com.chimaera;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "proposal_messages")
class ProposalMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "proposal_id") private Proposal proposal;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "sender_id") private UserProfile sender;
    @Column(nullable = false, length = 2000) private String body;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected ProposalMessage() {}
    ProposalMessage(Proposal proposal, UserProfile sender, String body) {
        this.proposal = proposal; this.sender = sender; this.body = body.trim();
    }
    Long id() { return id; }
    UserProfile sender() { return sender; }
    String body() { return body; }
    Instant createdAt() { return createdAt; }
}
