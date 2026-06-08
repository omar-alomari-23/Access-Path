#!/usr/bin/env python3
"""
AccessPath Integration Test Runner
===================================
Runs all 61 integration tests against the live backend.

Prerequisites:
  - Docker database running:  docker-compose up -d
  - Backend running:          JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run
  - Python 3 (stdlib only, no extra packages needed)

Usage:
  python3 docs/run_integration_tests.py

Each feature group prints its results separately so you can screenshot
each section individually (Appendix A through K in the Test Plan).
"""

import urllib.request
import json
import uuid
import sys
import random

BASE = "http://localhost:8080"

# Random offset ensures fresh coordinates each run (avoids duplicate detection
# from leftover reports in the database from previous runs).
_offset = random.uniform(0.5, 5.0)
def lat(base): return round(base + _offset, 6)
def lng(base): return round(base + _offset, 6)

# ── HTTP helpers ───────────────────────────────────────────────────────────────

def post(url, data, token=None):
    body = json.dumps(data).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + url, data=body, headers=headers)
    try:
        resp = urllib.request.urlopen(req)
        return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}


def get(url, token=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + url, headers=headers)
    try:
        resp = urllib.request.urlopen(req)
        return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}


# ── Result tracking ────────────────────────────────────────────────────────────

all_results = []

def check(name, passed):
    all_results.append((name, passed))
    print("  " + ("PASS" if passed else "FAIL") + "  " + name)


# ── Setup: login ───────────────────────────────────────────────────────────────

print("Setting up test accounts...")
_, alice_r = post("/api/auth/login", {"email": "alice@test.com",   "password": "Test1234!"})
_, bob_r   = post("/api/auth/login", {"email": "bob@test.com",     "password": "Test1234!"})
_, mod_r   = post("/api/auth/login", {"email": "modtest@test.com", "password": "Test1234!"})
alice = alice_r.get("token", "")
bob   = bob_r.get("token", "")
mod   = mod_r.get("token", "")

if not alice or not bob or not mod:
    print("ERROR: Could not log in to test accounts. Is the backend running?")
    sys.exit(1)
print("Login OK — alice (REPORTER), bob (NAVIGATOR), modtest (MODERATOR)\n")

# ══════════════════════════════════════════════════════════════════════════════
# Appendix A — Feature 1: User Registration & Login
# ══════════════════════════════════════════════════════════════════════════════
print("=" * 60)
print("Appendix A — Feature 1: User Registration & Login")
print("=" * 60)

s, r = post("/api/auth/register", {"email": "newuser_" + uuid.uuid4().hex[:6] + "@test.com", "password": "Test1234!", "role": "REPORTER"})
check("IT-1.1  successful registration → 201", s == 201 and "token" in r)
s, r = post("/api/auth/register", {"email": "alice@test.com", "password": "Test1234!", "role": "REPORTER"})
check("IT-1.2  duplicate email → 409", s == 409)
s, r = post("/api/auth/register", {"password": "Test1234!", "role": "REPORTER"})
check("IT-1.3  missing email → 400", s == 400 and "errors" in r)
s, r = post("/api/auth/register", {"email": "notanemail", "password": "Test1234!", "role": "REPORTER"})
check("IT-1.4  invalid email format → 400", s == 400)
s, r = post("/api/auth/register", {"email": "alice@test.com", "role": "REPORTER"})
check("IT-1.5  missing password → 400", s == 400)
s, r = post("/api/auth/register", {"email": "alice@test.com", "password": "Test1234!"})
check("IT-1.6  missing role → 400", s == 400)
s, r = post("/api/auth/login", {"email": "alice@test.com", "password": "Test1234!"})
check("IT-1.7  successful login → 200", s == 200 and "token" in r)
s, r = post("/api/auth/login", {"email": "alice@test.com", "password": "WrongPass"})
check("IT-1.8  wrong password → 401", s == 401)
s, r = post("/api/auth/login", {"email": "nobody@test.com", "password": "Test1234!"})
check("IT-1.9  unknown email → 401", s == 401)
s, r = post("/api/auth/login", {"email": "alice@test.com"})
check("IT-1.10 missing password field → 400", s == 400)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix B — Feature 2: Report Draft (AI Classification)
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix B — Feature 2: Report Draft (AI Classification)")
print("=" * 60)

s, r = post("/api/reports/draft", {"description": "The stairs at the north entrance are blocked", "latitude": lat(25.15), "longitude": lng(55.35)})
check("IT-2.1  stair keyword → suggestedCategory present", s == 200 and "suggestedCategory" in r)
s, r = post("/api/reports/draft", {"description": "Broken ramp near bus stop", "latitude": lat(25.15), "longitude": lng(55.35)})
check("IT-2.2  ramp keyword → suggestedCategory present", s == 200 and "suggestedCategory" in r)
s, r = post("/api/reports/draft", {"latitude": lat(25.15), "longitude": lng(55.35)})
check("IT-2.3  missing description → 400", s == 400 and "errors" in r)
s, r = post("/api/reports/draft", {"description": "Broken ramp", "longitude": 55.35})
check("IT-2.4  missing latitude → 400", s == 400)
s, r = post("/api/reports/draft", {"description": "Broken ramp", "latitude": 95.0, "longitude": 55.35})
check("IT-2.5  latitude > 90 → 400", s == 400)
s, r = post("/api/reports/draft", {"description": "Broken ramp", "latitude": 25.15, "longitude": -185.0})
check("IT-2.6  longitude < -180 → 400", s == 400)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix C — Feature 3: Report Submission & Duplicate Detection
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix C — Feature 3: Report Submission & Duplicate Detection")
print("=" * 60)

s, r = post("/api/reports", {"description": "Broken ramp near library", "latitude": lat(25.15), "longitude": lng(55.35), "category": "RAMP", "severity": "HIGH"}, alice)
check("IT-3.1  submit new report → 201 PENDING", s == 201 and r.get("status") == "PENDING")
report_id = r.get("reportId", "")
s, r = post("/api/reports", {"description": "Another ramp issue", "latitude": lat(25.15), "longitude": lng(55.35), "category": "RAMP", "severity": "HIGH"}, alice)
check("IT-3.2  duplicate nearby same category → 409", s == 409)
s, r = post("/api/reports", {"description": "Elevator broken", "latitude": lat(25.15), "longitude": lng(55.35), "category": "STEP_FREE_ISSUE", "severity": "HIGH"}, alice)
check("IT-3.3  different category same location → 201", s == 201)
report_id2 = r.get("reportId", "")
s, r = post("/api/reports", {"description": "test", "latitude": lat(25.15), "longitude": lng(55.35), "category": "OBSTRUCTION", "severity": "LOW"})
check("IT-3.4  no auth header → 401/403", s in [401, 403])
s, r = post("/api/reports", {"description": "test", "latitude": 90.0001, "longitude": 55.35, "category": "OBSTRUCTION", "severity": "LOW"}, alice)
check("IT-3.5  latitude just above 90 → 400", s == 400)
s, r = post("/api/reports", {"description": "test", "latitude": -90.0001, "longitude": 55.35, "category": "OBSTRUCTION", "severity": "LOW"}, alice)
check("IT-3.6  latitude just below -90 → 400", s == 400)
s, r = post("/api/reports", {"description": "test", "latitude": 25.15, "longitude": 180.0001, "category": "OBSTRUCTION", "severity": "LOW"}, alice)
check("IT-3.7  longitude just above 180 → 400", s == 400)
s, r = post("/api/reports", {"description": "test", "latitude": 25.15, "longitude": -180.0001, "category": "OBSTRUCTION", "severity": "LOW"}, alice)
check("IT-3.8  longitude just below -180 → 400", s == 400)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix D — Feature 4: View Report by ID
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix D — Feature 4: View Report by ID")
print("=" * 60)

s, r = get("/api/reports/" + report_id)
check("IT-4.1  fetch existing report → 200", s == 200 and r.get("reportId") == report_id)
s, r = get("/api/reports/" + str(uuid.uuid4()))
check("IT-4.2  fetch non-existent report → 404", s == 404)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix E — Feature 5: Nearby Report Lookup
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix E — Feature 5: Nearby Report Lookup")
print("=" * 60)

s, r = get("/api/reports/nearby?lat=" + str(lat(25.15)) + "&lng=" + str(lng(55.35)) + "&radius=200")
check("IT-5.1  returns nearby reports", s == 200 and any(x.get("reportId") == report_id for x in r))
s, r = get("/api/reports/nearby?lat=0.0&lng=0.0&radius=10")
check("IT-5.2  remote location → empty array", s == 200 and r == [])
s, r = get("/api/reports/nearby?lat=" + str(lat(25.15)) + "&lng=" + str(lng(55.35)))
check("IT-5.3  no radius → default applied", s == 200)
s, er = post("/api/reports", {"description": "Expiry nearby test", "latitude": lat(25.13), "longitude": lng(55.33), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
exp_id = er.get("reportId", "")
post("/api/moderation/" + exp_id + "/expire", {}, mod)
s, r = get("/api/reports/nearby?lat=" + str(lat(25.13)) + "&lng=" + str(lng(55.33)) + "&radius=200")
check("IT-5.4  expired report excluded from nearby", s == 200 and not any(x.get("reportId") == exp_id for x in r))

# ══════════════════════════════════════════════════════════════════════════════
# Appendix F — Feature 6: Community Voting
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix F — Feature 6: Community Voting")
print("=" * 60)

s, r = post("/api/reports/" + report_id + "/votes", {"voteType": "CONFIRM"}, bob)
check("IT-6.1  CONFIRM vote by different user → 201", s == 201 and r.get("voteType") == "CONFIRM")
_, carol_r = post("/api/auth/register", {"email": "carol_" + uuid.uuid4().hex[:6] + "@test.com", "password": "Test1234!", "role": "REPORTER"})
carol = carol_r.get("token", "")
s, r = post("/api/reports/" + report_id + "/votes", {"voteType": "DISPUTE"}, carol)
check("IT-6.2  DISPUTE vote by third user → 201", s == 201 and r.get("voteType") == "DISPUTE")
s, r = post("/api/reports/" + report_id + "/votes", {"voteType": "CONFIRM"}, alice)
check("IT-6.3  self-vote → 401", s == 401)
s, r = post("/api/reports/" + report_id + "/votes", {"voteType": "CONFIRM"}, bob)
check("IT-6.4  duplicate vote → 409", s == 409)
s, r = post("/api/reports/" + report_id + "/votes", {"voteType": "INVALID"}, bob)
check("IT-6.5  invalid vote type → 400", s == 400)
s, er2 = post("/api/reports", {"description": "Terminal vote test", "latitude": lat(25.14), "longitude": lng(55.34), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
exp2_id = er2.get("reportId", "")
post("/api/moderation/" + exp2_id + "/expire", {}, mod)
s, r = post("/api/reports/" + exp2_id + "/votes", {"voteType": "CONFIRM"}, bob)
check("IT-6.6  vote on terminal report → 409", s == 409)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix G — Feature 7: Confidence Score Calculation
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix G — Feature 7: Confidence Score Calculation")
print("=" * 60)

s, r = get("/api/reports/" + report_id)
check("IT-7.1  score after 1 confirm + 1 dispute (net zero) = 0.3", s == 200 and r.get("confidenceScore") == 0.3)
post("/api/moderation/" + report_id + "/verify", {"note": "Confirmed on-site"}, mod)
s, r = get("/api/reports/" + report_id)
check("IT-7.2  score after moderator verification = 0.6", s == 200 and r.get("confidenceScore") == 0.6)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix H — Feature 8: Moderation Queue & Actions
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix H — Feature 8: Moderation Queue & Actions")
print("=" * 60)

s, r = post("/api/reports", {"description": "Queue test report", "latitude": lat(25.16), "longitude": lng(55.36), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
q_id = r.get("reportId", "")
s, r = get("/api/moderation/queue", mod)
check("IT-8.1  view pending queue → 200", s == 200 and isinstance(r, list))
s, r = post("/api/moderation/" + q_id + "/verify", {"note": "Confirmed on-site"}, mod)
check("IT-8.2  verify report → 200 VERIFY", s == 200 and r.get("actionType") == "VERIFY")
s, r = post("/api/moderation/" + q_id + "/verify", {}, mod)
check("IT-8.3  verify already-verified → 409", s == 409)
s, rej_r = post("/api/reports", {"description": "To reject", "latitude": lat(25.17), "longitude": lng(55.37), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
rej_id = rej_r.get("reportId", "")
s, r = post("/api/moderation/" + rej_id + "/reject", {"note": "Insufficient evidence"}, mod)
check("IT-8.4  reject report → 200 REJECT", s == 200 and r.get("actionType") == "REJECT")
s, r = post("/api/moderation/" + q_id + "/resolve", {"note": "Issue repaired"}, mod)
check("IT-8.5  resolve verified report → 200 RESOLVE", s == 200 and r.get("actionType") == "RESOLVE")
s, pr = post("/api/reports", {"description": "Pending resolve test", "latitude": lat(25.18), "longitude": lng(55.38), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
p_id = pr.get("reportId", "")
s, r = post("/api/moderation/" + p_id + "/resolve", {}, mod)
check("IT-8.6  resolve pending report → 409", s == 409)
s, r = post("/api/moderation/" + p_id + "/expire", {}, mod)
check("IT-8.7  manually expire active report → 200 EXPIRE", s == 200 and r.get("actionType") == "EXPIRE")
s, r = post("/api/moderation/" + q_id + "/expire", {}, mod)
check("IT-8.8  expire terminal report → 409", s == 409)
s, r = get("/api/moderation/queue", alice)
check("IT-8.9  non-moderator → 403", s == 403)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix I — Feature 9: Route Planning
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix I — Feature 9: Route Planning")
print("=" * 60)

s, r = post("/api/routes", {"originLat": 25.15, "originLng": 55.35, "destinationLat": 25.20, "destinationLng": 55.40, "avoidStairs": True, "avoidSteepInclines": False, "avoidUnsafeAreas": False})
check("IT-9.1  get routes → 200, 3 routes sorted by suitability", s == 200 and len(r.get("routes", [])) == 3)
routes = r.get("routes", [])
check("IT-9.2  each route has 3 waypoints (origin/mid/dest)", all(len(rt.get("waypoints", [])) == 3 for rt in routes))
scores = [rt.get("suitabilityScore", 0) for rt in routes]
check("IT-9.3  routes sorted descending by suitabilityScore", scores == sorted(scores, reverse=True))
s, r = post("/api/routes", {"originLat": 25.15, "originLng": 55.35, "destinationLat": 25.20, "destinationLng": 55.40})
check("IT-9.4  preferences omitted → 200", s == 200)
s, r = post("/api/routes", {"originLat": 95.0, "originLng": 55.35, "destinationLat": 25.20, "destinationLng": 55.40})
check("IT-9.5  origin latitude out of range → 400", s == 400)
s, r = post("/api/routes", {"originLat": 25.15, "originLng": -185.0, "destinationLat": 25.20, "destinationLng": 55.40})
check("IT-9.6  origin longitude out of range → 400", s == 400)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix J — Feature 10: JWT Authentication & Security Filters
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix J — Feature 10: JWT Authentication & Security Filters")
print("=" * 60)

s, r = post("/api/reports", {"description": "jwt test", "latitude": lat(25.19), "longitude": lng(55.39), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
check("IT-10.1 valid JWT → 201", s == 201)
s, r = post("/api/reports", {"description": "jwt test", "latitude": lat(25.19), "longitude": lng(55.39), "category": "OBSTRUCTION", "severity": "LOW"}, "expired.token.here")
check("IT-10.2 invalid/expired JWT → 401", s == 401)
s, r = post("/api/reports", {"description": "jwt test", "latitude": lat(25.19), "longitude": lng(55.39), "category": "OBSTRUCTION", "severity": "LOW"}, "not.a.real.jwt")
check("IT-10.3 malformed token → 401", s == 401)
s, r = post("/api/auth/login", {"email": "alice@test.com", "password": "Test1234!"})
check("IT-10.4 public endpoint accessible without token → 200", s == 200)

# ══════════════════════════════════════════════════════════════════════════════
# Appendix K — Feature 11: Automatic Report Expiry (Scheduler)
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
print("Appendix K — Feature 11: Automatic Report Expiry (Scheduler)")
print("=" * 60)

s, r = post("/api/reports", {"description": "Expiry test PENDING", "latitude": lat(25.20), "longitude": lng(55.41), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
e1 = r.get("reportId", "")
s, r = post("/api/moderation/" + e1 + "/expire", {}, mod)
check("IT-11.1 PENDING report can be expired → 200", s == 200 and r.get("actionType") == "EXPIRE")

s, r = post("/api/reports", {"description": "Expiry test VERIFIED", "latitude": lat(25.21), "longitude": lng(55.42), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
e2 = r.get("reportId", "")
post("/api/moderation/" + e2 + "/verify", {}, mod)
s, r = post("/api/moderation/" + e2 + "/expire", {}, mod)
check("IT-11.2 VERIFIED report can be expired → 200", s == 200 and r.get("actionType") == "EXPIRE")

s, r = post("/api/reports", {"description": "Reject expiry test", "latitude": lat(25.22), "longitude": lng(55.43), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
e3 = r.get("reportId", "")
post("/api/moderation/" + e3 + "/reject", {}, mod)
s, r = post("/api/moderation/" + e3 + "/expire", {}, mod)
check("IT-11.3 REJECTED report cannot be expired → 409", s == 409)

s, r = post("/api/reports", {"description": "Resolve expiry test", "latitude": lat(25.23), "longitude": lng(55.44), "category": "OBSTRUCTION", "severity": "LOW"}, alice)
e4 = r.get("reportId", "")
post("/api/moderation/" + e4 + "/verify", {}, mod)
post("/api/moderation/" + e4 + "/resolve", {}, mod)
s, r = post("/api/moderation/" + e4 + "/expire", {}, mod)
check("IT-11.4 RESOLVED report cannot be expired → 409", s == 409)

# ══════════════════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════════════════
print()
print("=" * 60)
total  = len(all_results)
passed = sum(1 for _, p in all_results if p)
failed = [(n, p) for n, p in all_results if not p]
print("RESULT: " + str(passed) + "/" + str(total) + " PASS")
if failed:
    print("\nFAILED:")
    for n, _ in failed:
        print("  - " + n)
print("=" * 60)
