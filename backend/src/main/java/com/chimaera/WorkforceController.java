package com.chimaera;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Entity @Table(name="workforce_profiles")
class WorkforceProfile {
    @Id UUID userId;
    int experienceYears;
    int weeklyHours;
    int externalHours;
    protected WorkforceProfile() {}
    WorkforceProfile(UUID id) { userId = id; }
}
interface WorkforceRepository extends JpaRepository<WorkforceProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WorkforceProfile p where p.userId = :id")
    Optional<WorkforceProfile> lock(@Param("id") UUID id);
}
@Entity @Table(name="workforce_tasks")
class WorkforceTask {
    @Id @GeneratedValue UUID id;
    UUID questId;
    UUID employeeId;
    UUID proposalId;
    @Column(length=200) String title;
    @Column(length=2000) String skills;
    int hours;
    @Enumerated(EnumType.STRING) @Column(name="assignment_status") WorkforceTaskStatus status = WorkforceTaskStatus.PENDING;
    boolean completed;
    void respond(ProposalStatus decision) {
        if (status != WorkforceTaskStatus.PENDING) return;
        status = decision == ProposalStatus.ACCEPTED ? WorkforceTaskStatus.ACCEPTED : WorkforceTaskStatus.DECLINED;
    }
}
enum WorkforceTaskStatus { PENDING, ACCEPTED, DECLINED }
interface WorkforceTaskRepository extends JpaRepository<WorkforceTask, UUID> {
    List<WorkforceTask> findByEmployeeIdAndCompletedFalseAndStatus(UUID employeeId, WorkforceTaskStatus status);
    List<WorkforceTask> findByEmployeeIdAndCompletedFalseAndStatusIn(UUID employeeId, Collection<WorkforceTaskStatus> statuses);
    List<WorkforceTask> findByQuestId(UUID questId);
    List<WorkforceTask> findByQuestIdAndStatusIn(UUID questId, Collection<WorkforceTaskStatus> statuses);
    List<WorkforceTask> findByProposalId(UUID proposalId);
}
record WorkforceDetails(@Min(0) @Max(60) int experienceYears,
                        @Min(0) @Max(80) int weeklyHours, @Min(0) @Max(80) int externalHours) {}
record WorkItem(@NotBlank @Size(max=200) String title,
                @NotEmpty @Size(max=10) List<@NotBlank @Size(max=120) String> skills,
                @Min(1) @Max(80) int hours) {}
record PlanningRequest(@NotEmpty @Size(max=30) List<@Valid WorkItem> tasks, @Min(1) @Max(20) int teamSize) {}
record Allocation(@NotNull UUID employeeId, @NotNull @Valid WorkItem task) {}
record AllocateRequest(boolean approved, @NotEmpty @Size(max=30) List<@Valid Allocation> assignments) {}
record TaskView(UUID id, UUID projectId, String project, UUID employeeId, String employee,
                String title, String skills, int hours, WorkforceTaskStatus status, boolean completed) {}

@RestController @RequestMapping("/api/workforce")
class WorkforceController {
    private final QuestService quests;
    private final QuestRepository projects;
    private final WorkforceRepository workforce;
    private final WorkforceTaskRepository tasks;
    private final UserProfileRepository users;
    private final UserSkillRepository skills;
    private final ProposalRepository proposals;
    private final AiClient ai;
    WorkforceController(QuestService quests, QuestRepository projects, WorkforceRepository workforce,
                        WorkforceTaskRepository tasks, UserProfileRepository users, UserSkillRepository skills,
                        ProposalRepository proposals, AiClient ai) {
        this.quests=quests; this.projects=projects; this.workforce=workforce;
        this.tasks=tasks; this.users=users; this.skills=skills; this.proposals=proposals; this.ai=ai;
    }
    @GetMapping("/profile") @Transactional(readOnly=true)
    Map<String,Object> profile() {
        var p=workforce.findById(quests.currentUser().id()).orElse(new WorkforceProfile(quests.currentUser().id()));
        return Map.of("experienceYears",p.experienceYears,"weeklyHours",p.weeklyHours,
                      "externalHours",p.externalHours,"assignedHours",load(p.userId));
    }
    @PutMapping("/profile") @Transactional
    void saveProfile(@Valid @RequestBody WorkforceDetails details) {
        var id=quests.currentUser().id();
        var p=workforce.lock(id).orElse(new WorkforceProfile(id));
        if(details.externalHours()>details.weeklyHours()) throw conflict("Existing workload exceeds weekly capacity");
        p.experienceYears=details.experienceYears(); p.weeklyHours=details.weeklyHours(); p.externalHours=details.externalHours();
        workforce.save(p);
    }
    @PostMapping("/projects/{id}/plan") @Transactional(readOnly=true)
    Map<String,Object> plan(@PathVariable UUID id, @Valid @RequestBody PlanningRequest request) {
        quests.get(id);
        var employees=new ArrayList<Map<String,Object>>();
        for(var p:workforce.findAll()) {
            var u=users.findById(p.userId).orElseThrow();
            if(!eligible(u)) continue;
            employees.add(Map.of("id",p.userId.toString(),"name",u.displayName(),
                "skills",skills.findByUser(u).stream().map(s->s.skill().name()).toList(),
                "experience_years",p.experienceYears,"weekly_hours",p.weeklyHours,
                "workload_hours",p.externalHours+load(p.userId),"availability",u.availabilityStatus()));
        }
        return ai.plan(request,employees);
    }
    @PostMapping("/projects/{id}/allocate") @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    List<TaskView> allocate(@PathVariable UUID id, @Valid @RequestBody AllocateRequest request) {
        var project=quests.get(id);
        if(!request.approved()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Approval is required");
        projects.lock(id).orElseThrow();
        // Serialize by employee in stable order across projects, then check actual load.
        var locked=new HashMap<UUID,WorkforceProfile>();
        request.assignments().stream().map(Allocation::employeeId).distinct().sorted().forEach(employee ->
            locked.put(employee,workforce.lock(employee).orElseThrow(()->conflict("Employee capacity is missing"))));
        if(!tasks.findByQuestIdAndStatusIn(id,List.of(WorkforceTaskStatus.PENDING,WorkforceTaskStatus.ACCEPTED)).isEmpty())
            throw conflict("This project already has an active allocation or pending invitation");
        if(locked.size()>project.maxMembers()) throw conflict("Team exceeds the project size limit");
        var pending=new HashMap<UUID,Integer>();
        for(var item:request.assignments()) {
            var employee=users.findById(item.employeeId()).orElseThrow();
            if(!eligible(employee)) throw conflict("Employee is no longer available");
            var actual=skills.findByUser(employee).stream().map(s->Skill.normalize(s.skill().name())).toList();
            if(!item.task().skills().stream().allMatch(s->actual.contains(Skill.normalize(s)))) throw conflict("Employee lacks required task skills");
            var p=locked.get(item.employeeId());
            int added=pending.merge(p.userId,item.task().hours(),Integer::sum);
            if(p.externalHours+load(p.userId)+added>p.weeklyHours) throw conflict("Capacity changed. Rebuild the team plan.");
        }
        var invitations=new HashMap<UUID,Proposal>();
        for(var employeeId:locked.keySet()) {
            var receiver=users.findById(employeeId).orElseThrow();
            var proposal=new Proposal(project,receiver,employeeId.toString(),
                "You have been invited to join " + project.title() + ". Review the proposed work and accept before tasks are assigned.");
            proposal.send(); invitations.put(employeeId,proposals.save(proposal));
        }
        for(var item:request.assignments()) {
            var task=new WorkforceTask(); task.questId=id; task.employeeId=item.employeeId();
            task.proposalId=invitations.get(item.employeeId()).id();
            task.title=item.task().title(); task.skills=String.join(", ",item.task().skills()); task.hours=item.task().hours(); tasks.save(task);
        }
        return tasks.findByQuestId(id).stream().map(this::view).toList();
    }
    @GetMapping("/projects") @Transactional(readOnly=true)
    List<Map<String,Object>> projects() {
        var owner=quests.currentUser().id();
        return projects.findByOwner_Id(owner).stream().map(p->Map.<String,Object>of("id",p.id(),"title",p.title(),"requirements",List.copyOf(p.requirements()))).toList();
    }
    @GetMapping("/projects/{id}/tasks") @Transactional(readOnly=true)
    List<TaskView> projectTasks(@PathVariable UUID id) { quests.get(id); return tasks.findByQuestId(id).stream().map(this::view).toList(); }
    @GetMapping("/tasks") @Transactional(readOnly=true)
    List<TaskView> ownTasks() { return tasks.findByEmployeeIdAndCompletedFalseAndStatus(quests.currentUser().id(),WorkforceTaskStatus.ACCEPTED).stream().map(this::view).toList(); }
    @PostMapping("/tasks/{id}/complete") @Transactional
    void complete(@PathVariable UUID id) {
        var task=tasks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var user=quests.currentUser().id();
        if(task.status != WorkforceTaskStatus.ACCEPTED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Accept the collaboration invitation first");
        if(!task.employeeId.equals(user) && !projects.findById(task.questId).orElseThrow().owner().id().equals(user))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        workforce.lock(task.employeeId).orElseThrow();
        task.completed=true;
    }
    private int load(UUID id) { return tasks.findByEmployeeIdAndCompletedFalseAndStatusIn(id,List.of(WorkforceTaskStatus.PENDING,WorkforceTaskStatus.ACCEPTED)).stream().mapToInt(t->t.hours).sum(); }
    private boolean eligible(UserProfile u) { return u.active() && !u.availabilityStatus().equals("UNAVAILABLE") && !u.profileVisibility().equals("PRIVATE"); }
    private TaskView view(WorkforceTask t) { return new TaskView(t.id,t.questId,projects.findById(t.questId).orElseThrow().title(),t.employeeId,users.findById(t.employeeId).orElseThrow().displayName(),t.title,t.skills,t.hours,t.status,t.completed); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
