import unittest
from pydantic import ValidationError
from app.main import AnalyseRequest, Candidate, RankRequest, analyse, rank


class LiveMatchingTests(unittest.TestCase):
    def test_new_catalog_skill_is_detected_without_code_changes(self):
        result = analyse(AnalyseRequest(text="Need a Rust specialist", skill_catalog=["Rust", "R"]))
        self.assertEqual(result["capabilities"], ["rust"])

    def test_real_skill_profiles_determine_order(self):
        candidates = [Candidate(id="one", name="One", skills=["rust", "sql"]),
                      Candidate(id="two", name="Two", skills=["rust"]),
                      Candidate(id="three", name="Three", skills=["photography"])]
        result = rank(RankRequest(required_skills=["rust"], candidates=candidates))
        self.assertEqual([r["id"] for r in result], ["two", "one"])
        self.assertTrue(all("TF-IDF" in r["reasons"][1] for r in result))

    def test_empty_and_unrelated_requests_have_no_fake_matches(self):
        self.assertEqual(rank(RankRequest(required_skills=[], candidates=[])), [])
        self.assertEqual(rank(RankRequest(required_skills=["C++"], candidates=[Candidate(id="c", name="C", skills=["C"])])), [])

    def test_rejects_oversized_matching_payloads(self):
        with self.assertRaises(ValidationError):
            RankRequest(required_skills=[f"skill-{index}" for index in range(31)], candidates=[])
        with self.assertRaises(ValidationError):
            Candidate(id="candidate", name="Candidate", skills=[f"skill-{index}" for index in range(101)])
