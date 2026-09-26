package com.chimaera;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ProposalMessageRepository extends JpaRepository<ProposalMessage, Long> {
    List<ProposalMessage> findByProposal_IdOrderByCreatedAtAscIdAsc(UUID proposalId);
}

record NewChatMessage(@NotBlank @Size(max = 2000) String body) {}
record ChatMessageView(Long id, UUID senderId, String senderName, String body, Instant createdAt, boolean mine) {}

@RestController
@RequestMapping("/api/proposals/{proposalId}/messages")
class ProposalChatController {
    private final AuthIdentityRepository identities;
    private final ProposalRepository proposals;
    private final ProposalMessageRepository messages;
    ProposalChatController(AuthIdentityRepository identities, ProposalRepository proposals, ProposalMessageRepository messages) {
        this.identities = identities; this.proposals = proposals; this.messages = messages;
    }
    private UserProfile current(Jwt jwt) {
        return identities.findByProviderAndSubject("supabase", jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete profile setup first")).user();
    }
    private Proposal accepted(UUID proposalId, UserProfile user) {
        Proposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        boolean sender = proposal.quest().owner() != null && proposal.quest().owner().id().equals(user.id());
        boolean receiver = proposal.receiver() != null && proposal.receiver().id().equals(user.id());
        if ((!sender && !receiver) || proposal.status() != ProposalStatus.ACCEPTED)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        return proposal;
    }
    @GetMapping @Transactional
    List<ChatMessageView> list(@PathVariable UUID proposalId, @AuthenticationPrincipal Jwt jwt) {
        UserProfile user = current(jwt); accepted(proposalId, user);
        return messages.findByProposal_IdOrderByCreatedAtAscIdAsc(proposalId).stream().map(m -> view(m, user)).toList();
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    ChatMessageView send(@PathVariable UUID proposalId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewChatMessage body) {
        UserProfile user = current(jwt); Proposal proposal = accepted(proposalId, user);
        return view(messages.save(new ProposalMessage(proposal, user, body.body())), user);
    }
    private ChatMessageView view(ProposalMessage message, UserProfile user) {
        return new ChatMessageView(message.id(), message.sender().id(), message.sender().displayName(), message.body(), message.createdAt(), message.sender().id().equals(user.id()));
    }
}
