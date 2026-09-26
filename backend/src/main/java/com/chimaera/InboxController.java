package com.chimaera;

import jakarta.transaction.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record InboxProposalView(UUID id, UUID questId, String questTitle, String message,
                         ProposalStatus status, Instant receivedAt, Instant respondedAt,
                         String senderName, String receiverName, String summary, java.time.LocalDate deadline, List<String> skills) {}
record ProposalDecision(@jakarta.validation.constraints.NotNull ProposalStatus decision) {}

@RestController
@RequestMapping("/api/inbox")
@ConditionalOnProperty(name = "chimaera.auth.enabled", havingValue = "true")
class InboxController {
    private final AuthIdentityRepository identities;
    private final ProposalRepository proposals;
    private final WorkforceTaskRepository tasks;

    InboxController(AuthIdentityRepository identities, ProposalRepository proposals, WorkforceTaskRepository tasks) {
        this.identities = identities;
        this.proposals = proposals;
        this.tasks = tasks;
    }

    Proposal received(UUID id, Jwt jwt, boolean lock) {
        var user = identities.findByProviderAndSubject("supabase", jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete profile setup first")).user();
        var p = (lock ? proposals.findForUpdate(id) : proposals.findById(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (p.status() == ProposalStatus.DRAFT || p.receiver() == null || !p.receiver().id().equals(user.id()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        return p;
    }
    @GetMapping("/proposals/{id}") @Transactional
    InboxProposalView review(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return view(received(id, jwt, false)); }
    @PostMapping("/proposals/{id}/respond") @Transactional
    InboxProposalView respond(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @jakarta.validation.Valid @RequestBody ProposalDecision body) {
        var p = received(id, jwt, true); p.respond(body.decision());
        tasks.findByProposalId(p.id()).forEach(task -> task.respond(body.decision()));
        return view(p);
    }
    @DeleteMapping("/proposals/{id}") @Transactional
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        var proposal = received(id, jwt, true);
        if (proposal.status() == ProposalStatus.SENT) {
            proposal.respond(ProposalStatus.DECLINED);
            tasks.findByProposalId(proposal.id()).forEach(task -> task.respond(ProposalStatus.DECLINED));
        }
        proposal.hideFromReceiver();
    }
    private InboxProposalView view(Proposal p) {
        var q = p.quest();
        return new InboxProposalView(p.id(), q.id(), q.title(), p.message(), p.status(),
                p.sentAt() == null ? p.createdAt() : p.sentAt(), p.respondedAt(),
                q.owner() == null ? "Former member" : q.owner().displayName(),
                p.receiver() == null ? "Former member" : p.receiver().displayName(),
                q.publicSummary(), q.deadline(), List.copyOf(q.requirements()));
    }

    @GetMapping("/proposals")
    @Transactional
    List<InboxProposalView> proposals(@AuthenticationPrincipal Jwt jwt) {
        UserProfile user = identities.findByProviderAndSubject("supabase", jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete profile setup first"))
                .user();
        return proposals.findByReceiver_IdOrderByCreatedAtDesc(user.id()).stream()
                .filter(proposal -> proposal.status() != ProposalStatus.DRAFT)
                .filter(proposal -> !proposal.hiddenFromReceiver())
                .map(this::view)
                .toList();
    }

    @GetMapping("/sent") @Transactional
    List<InboxProposalView> sent(@AuthenticationPrincipal Jwt jwt) {
        UserProfile user = identities.findByProviderAndSubject("supabase", jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete profile setup first")).user();
        return proposals.findByQuest_Owner_IdOrderByCreatedAtDesc(user.id()).stream()
                .filter(proposal -> proposal.status() != ProposalStatus.DRAFT)
                .map(this::view)
                .toList();
    }
}
