package com.chimaera;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class WorkforceFlowTest {
    @Autowired AuthProfileService profiles;
    @Autowired QuestService quests;
    @Autowired WorkforceController workforce;
    @Autowired ProposalRepository proposals;
    @Autowired WorkforceTaskRepository tasks;
    void login(String subject) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
            Jwt.withTokenValue("test").header("alg","RS256").subject(subject).build()));
    }
    ProfileView employee(String name) {
        var p=profiles.sync(name,name+"@example.test",new ProfileSyncRequest(name,"","",List.of("python"),List.of(),"AVAILABLE","MEMBERS","UTC"));
        login(name); workforce.saveProfile(new WorkforceDetails(3,8,2)); return p;
    }
    Quest project(String name) { login(name); return quests.create(new CreateQuest("Test project","API","Build a Python API",LocalDate.now().plusDays(7),3,List.of("python"))); }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }
    @Test void allocationsRequireAcceptanceBeforeAppearingInEmployeeInbox() {
        employee("manager-flow"); var worker=employee("worker-flow"); var q=project("manager-flow");
        var request=new AllocateRequest(true,List.of(new Allocation(worker.id(),new WorkItem("Build API",List.of("python"),4))));
        var saved=workforce.allocate(q.id(),request); assertEquals(1,saved.size());
        login("worker-flow"); assertTrue(workforce.ownTasks().isEmpty()); assertEquals(4,workforce.profile().get("assignedHours"));
        var invitation=proposals.findByReceiver_IdOrderByCreatedAtDesc(worker.id()).get(0);
        invitation.respond(ProposalStatus.ACCEPTED);
        tasks.findByProposalId(invitation.id()).forEach(task -> task.respond(ProposalStatus.ACCEPTED));
        assertEquals(1,workforce.ownTasks().size());
        workforce.complete(saved.get(0).id()); assertEquals(0,workforce.profile().get("assignedHours")); assertTrue(workforce.ownTasks().isEmpty());
        login("manager-flow"); assertTrue(workforce.projectTasks(q.id()).get(0).completed());
    }
    @Test void rejectsOverCapacityAndMissingSkills() {
        employee("manager-capacity"); var worker=employee("worker-capacity"); var q=project("manager-capacity");
        assertThrows(ResponseStatusException.class,()->workforce.allocate(q.id(),new AllocateRequest(true,List.of(new Allocation(worker.id(),new WorkItem("Too much",List.of("python"),7))))));
    }
    @Test void otherEmployeeCannotViewOrAllocateOwnersProject() {
        employee("manager-access"); var stranger=employee("stranger-access"); var q=project("manager-access");
        login("stranger-access"); assertThrows(ResponseStatusException.class,()->workforce.projectTasks(q.id()));
    }
    @Test void allocationRequiresExplicitApproval() {
        employee("manager-approval"); var worker=employee("worker-approval"); var q=project("manager-approval");
        assertThrows(ResponseStatusException.class,()->workforce.allocate(q.id(),new AllocateRequest(false,List.of(new Allocation(worker.id(),new WorkItem("API",List.of("python"),4))))));
    }
}
