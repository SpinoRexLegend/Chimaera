from fastapi import FastAPI
from pydantic import BaseModel, Field
import re
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

app = FastAPI(title="CHIMAERA AI/ML Service", version="0.1.0")

class AnalyseRequest(BaseModel):
    text: str = Field(min_length=1, max_length=8000)
    explicit_skills: list[str] = Field(default_factory=list, max_length=30)
    skill_catalog: list[str] = Field(default_factory=list, max_length=2000)

class Candidate(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=120)
    skills: list[str] = Field(max_length=100)
    experience_years: int = Field(default=0, ge=0, le=60)
    weekly_hours: int = Field(default=0, ge=0, le=80)
    workload_hours: int = Field(default=0, ge=0)
    availability: str = "AVAILABLE"

class Task(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    skills: list[str] = Field(min_length=1, max_length=10)
    hours: int = Field(ge=1, le=80)

class PlanRequest(BaseModel):
    tasks: list[Task] = Field(min_length=1, max_length=30)
    team_size: int = Field(ge=1, le=20)
    candidates: list[Candidate] = Field(max_length=1000)

@app.post("/plan")
def plan(request: PlanRequest):
    """TF-IDF relevance with capacity constraints and transparent tie-breaks.

    Experience and remaining capacity break equal relevance scores; these are
    scheduling rules, not a learned prediction of employee performance.
    """
    remaining = {c.id: max(0, c.weekly_hours - c.workload_hours) for c in request.candidates}
    selected, assignments, unassigned = set(), [], []
    # Scarce skills first; retain input order for equally constrained tasks.
    def eligible(task):
        needs = {s.strip().casefold() for s in task.skills}
        return [c for c in request.candidates if c.availability != "UNAVAILABLE"
                and needs <= {s.strip().casefold() for s in c.skills}
                and remaining[c.id] >= task.hours]
    ordered = sorted(enumerate(request.tasks), key=lambda pair: len(eligible(pair[1])))
    for index, task in ordered:
        candidates = [c for c in eligible(task) if c.id in selected or len(selected) < request.team_size]
        if not candidates:
            unassigned.append({"index": index, "task": task.model_dump(), "reason": "No employee with all required skills and sufficient remaining capacity within the team limit."})
            continue
        scores = {r["id"]: r["match_score"] for r in rank(RankRequest(required_skills=task.skills, candidates=candidates))}
        chosen = min(candidates, key=lambda c: (-scores.get(c.id, 0), -c.experience_years, -remaining[c.id], c.id))
        selected.add(chosen.id)
        remaining[chosen.id] -= task.hours
        assignments.append({"index": index, "employeeId": chosen.id, "employee": chosen.name,
                            "task": task.model_dump(), "similarity": scores.get(chosen.id, 0),
                            "reason": f"All task skills covered; {chosen.experience_years} years experience; {remaining[chosen.id]} hours remaining after this allocation."})
    return {"assignments": sorted(assignments, key=lambda a: a["index"]), "unassigned": unassigned,
            "team": [{"id": c.id, "name": c.name, "remainingHours": remaining[c.id]} for c in request.candidates if c.id in selected],
            "model": "TF-IDF skill relevance; experience and free-capacity tie-breaks; capacity-constrained greedy scheduling"}

class RankRequest(BaseModel):
    required_skills: list[str] = Field(max_length=30)
    candidates: list[Candidate] = Field(max_length=1000)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/analyse")
def analyse(request: AnalyseRequest):
    capabilities = sorted({s.strip().casefold() for s in request.explicit_skills if s.strip()} | {
        s.strip().casefold() for s in request.skill_catalog if s.strip() and
        re.search(r'(?<!\w)' + re.escape(s.strip()) + r'(?!\w)', request.text, re.IGNORECASE)
    })
    summary = f"Detected {len(capabilities)} capability requirement(s). Review and confirm them before matching."
    return {"capabilities": capabilities, "summary": summary, "requires_owner_confirmation": True}

@app.post("/rank")
def rank(request: RankRequest):
    required = {skill.strip().casefold() for skill in request.required_skills if skill.strip()}
    if not required or not request.candidates:
        return []
    # Learn vocabulary and inverse document frequencies from the actual candidate pool.
    # Whole skill names are features, preserving distinctions such as C and C++.
    model = TfidfVectorizer(analyzer=lambda values: sorted(set(s.strip().casefold() for s in values if s.strip())))
    if not any(c.skills for c in request.candidates):
        return []
    matrix = model.fit_transform([c.skills for c in request.candidates])
    similarities = cosine_similarity(model.transform([list(required)]), matrix)[0]
    ranked = []
    for candidate, value in zip(request.candidates, similarities):
        overlap = sorted(required & {s.strip().casefold() for s in candidate.skills})
        if not overlap:
            continue
        ranked.append({"id": candidate.id, "name": candidate.name, "match_score": round(float(value), 5),
                       "reasons": ["Matched skills: " + ", ".join(overlap), "TF-IDF skill similarity; not a success probability"],
                       "model_version": "tfidf-live-skills-v1"})
    return sorted(ranked, key=lambda item: (-item["match_score"], item["id"]))
