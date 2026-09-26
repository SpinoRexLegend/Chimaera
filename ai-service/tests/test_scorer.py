import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.scorer import CandidateFeatures, extract_capabilities, score

class ScorerTests(unittest.TestCase):
    def test_extracts_aliases_and_explicit_skills(self):
        self.assertEqual(extract_capabilities("Need a UI developer for REST integration", ["Accessibility"]), ["accessibility", "react", "rest api"])

    def test_score_is_explainable_and_bounded(self):
        value, reasons = score(CandidateFeatures(1.0, 1.0, 0.5, 0))
        self.assertEqual(value, 0.925)
        self.assertEqual(len(reasons), 4)
        self.assertTrue(all(0 <= score(CandidateFeatures(v, v, v, 0))[0] <= 1 for v in (-2, 0, 2)))

if __name__ == "__main__":
    unittest.main()
