from __future__ import annotations

from dataclasses import dataclass

SKILL_ALIASES = {
    "react": {"react", "frontend", "front-end", "ui"},
    "javascript": {"javascript", "js", "typescript"},
    "spring boot": {"spring", "spring boot", "java", "backend"},
    "machine learning": {"machine learning", "ml", "scikit-learn", "sklearn"},
    "data visualization": {"data visualization", "visualisation", "charts", "dashboard"},
    "rest api": {"rest", "api", "integration"},
    "ux design": {"ux", "user experience", "design"},
    "testing": {"testing", "qa", "test automation"},
}

def extract_capabilities(text: str, explicit: list[str] | None = None) -> list[str]:
    normalized = text.casefold()
    found = {skill for skill, aliases in SKILL_ALIASES.items() if any(alias in normalized for alias in aliases)}
    found.update(item.strip().casefold() for item in explicit or [] if item.strip())
    return sorted(found)

@dataclass(frozen=True)
class CandidateFeatures:
    skill_coverage: float
    availability_overlap: float
    interest_similarity: float
    current_load: int

def score(features: CandidateFeatures) -> tuple[float, list[str]]:
    load_factor = max(0.0, 1.0 - min(features.current_load, 4) / 4)
    value = 0.55 * bounded(features.skill_coverage) + 0.25 * bounded(features.availability_overlap) + 0.15 * bounded(features.interest_similarity) + 0.05 * load_factor
    reasons = [
        f"Skill coverage: {features.skill_coverage:.0%}",
        f"Availability overlap: {features.availability_overlap:.0%}",
        f"Interest alignment: {features.interest_similarity:.0%}",
        "Collaboration load: available" if features.current_load <= 1 else "Collaboration load: limited",
    ]
    return round(bounded(value), 4), reasons

def bounded(value: float) -> float:
    return max(0.0, min(1.0, value))
