import unittest
from app.main import Candidate, Task, PlanRequest, plan

class WorkforceTests(unittest.TestCase):
    def employee(self, id, **kw):
        return Candidate(id=id,name=id,skills=kw.pop('skills',['python']),weekly_hours=kw.pop('weekly_hours',8),**kw)

    def test_never_exceeds_capacity_and_reports_unassigned_work(self):
        result=plan(PlanRequest(team_size=2,candidates=[self.employee('a',workload_hours=4)],
            tasks=[Task(title='API',skills=['python'],hours=4),Task(title='Tests',skills=['python'],hours=4)]))
        self.assertEqual(len(result['assignments']),1)
        self.assertEqual(len(result['unassigned']),1)
        self.assertEqual(result['team'][0]['remainingHours'],0)

    def test_experience_breaks_equal_skill_relevance(self):
        result=plan(PlanRequest(team_size=1,candidates=[self.employee('junior',experience_years=1),self.employee('senior',experience_years=8)],
            tasks=[Task(title='API',skills=['python'],hours=4)]))
        self.assertEqual(result['assignments'][0]['employeeId'],'senior')

    def test_team_limit_and_all_task_skills_are_required(self):
        result=plan(PlanRequest(team_size=1,candidates=[self.employee('a'),self.employee('b',skills=['react'])],
            tasks=[Task(title='Backend',skills=['python'],hours=4),Task(title='Frontend',skills=['react'],hours=4),
                   Task(title='Full stack',skills=['python','react'],hours=4)]))
        self.assertEqual(len(result['team']),1)
        self.assertEqual(len(result['unassigned']),2)

    def test_unavailable_and_unknown_capacity_are_excluded(self):
        result=plan(PlanRequest(team_size=2,candidates=[self.employee('a',availability='UNAVAILABLE'),self.employee('b',weekly_hours=0)],
            tasks=[Task(title='API',skills=['python'],hours=1)]))
        self.assertEqual(result['assignments'],[])

    def test_free_capacity_breaks_equal_experience(self):
        result=plan(PlanRequest(team_size=1,candidates=[self.employee('busy',workload_hours=6),self.employee('free')],
            tasks=[Task(title='API',skills=['python'],hours=2)]))
        self.assertEqual(result['assignments'][0]['employeeId'],'free')
