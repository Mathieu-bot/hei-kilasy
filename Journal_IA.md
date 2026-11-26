# AI Journal — Kilasy

---

## Change C1: late penalty on submissions

**Tool used:** Claude Code (Sonnet 5), CLI

### Exchange 1 — 22:45

> **Request:** "Have you understood the Kilasy onboarding?"

**Proposal**
A summary of the project: the module's rules (no reorganizing, one AI journal per exercise), the stack (Java 21, Spring Boot 4, hand-written JDBC, PostgreSQL), the API endpoints, and the business rules (one submission per exercise, late after the deadline day, points converted to a grade out of 20, grade sent by email).

**Decision:** kept.
**Why:** it matched the onboarding lesson.

**Verification**
Compared with the lesson "Prise en main de Kilasy": same rules, same endpoints.

---

### Exchange 2 — 22:46

> **Request:** the C1 assignment: "Late submissions are penalized: -10% of the grade per day of delay started, capped at -50%. The penalized grade must be the same in the grading, the transcript and the CSV export, never negative, and on-time submissions are not penalized."

**Proposal**
The AI first read the code and found 3 classes that each compute the /20 grade on their own: `SubmissionController` (in `get()` and `grade()`), `GradeReport` and `CsvExport`. It then implemented the penalty directly in these 4 places, without showing me a plan first:

| Term | Value |
|---|---|
| `daysLate` | whole days between the deadline and the submission date |
| `penalty` | `min(0.5, daysLate * 0.10)` |
| `grade` | `raw grade * (1 - penalty)` |

One small private helper inside `SubmissionController`, reused by its own `get()` and `grade()`. Each file keeps its existing rounding style (`Math.round`, `BigDecimal` HALF_UP, `floor`).

**Decision:** kept.
**Why:** it respects the "no reorganizing" rule and gives the 4 expected grades. Since the AI coded before showing a plan, the decision was made on the result.

**Verification**
Graded the 4 demo submissions on "Tri à bulles" with 16/20:

| Student | Days late | Grade |
|---|---|---|
| Alice | 0 | 16 |
| Bob | 1 | 14.4 |
| Clara | 3 | 11.2 |
| David | 8 (capped) | 8 |

Identical in the grading response, `GET /submissions/{id}`, `GET /students/{id}/transcript` and `GET /exports/grades.csv`. OK. Commit `C1` at 22:49.

---

### Summary

| File | Why it changed |
|---|---|
| `SubmissionController.java` | grading (`PUT /submissions/{id}/grade`) and submission detail (`GET /submissions/{id}`) both compute the grade here |
| `GradeReport.java` | the transcript computes the grade again, separately |
| `CsvExport.java` | the CSV export computes the grade a third time, separately |

No automated test added: the project has no test framework. Verified manually with `curl` against the demo data.

---

## Change C2: SMS notification when a grade is published

**Tool used:** Claude Sonnet 5.5, CLI

### Exchange 1 — 00:10

> **Request:** "Explore the code and propose a solution for C2, without modifying anything yet."

**Proposal**
- Everything happens in `SubmissionController.grade()`: the grade is saved, then the email is sent. The student's phone is already fetched by the SQL query.
- Declare a small `SmsSender` interface inside `SubmissionController`, and its log implementation as a `@Bean` in `KilasyApplication`: no new file.
- Send the SMS after the email, inside a `try/catch`, so a failure never cancels the published grade.
- Skip the SMS if the phone is empty; format the grade with a comma (`14,4`).

**Decision:** kept.
**Why:** it meets the 3 criteria while only modifying existing files, as the module requires. Changing provider means replacing the `@Bean` only.

**Verification**
Checked the facts it relied on myself: `KilasyApplication` is a plain `@SpringBootApplication`, the phone is in the `SELECT`, the grade is saved before the email, and there is no transaction.

---

### Exchange 2 — 00:14

> **Request:** "Implement it exactly as proposed, then compile. Don't run the app and don't commit: I verify myself."

**Proposal**
The implementation: 2 files changed, 27 lines added, compilation OK.

**Decision:** kept.
**Why:** the diff matches the accepted proposal line for line, with nothing extra.

**Verification**

| Criterion | Test | Result |
|---|---|---|
| Email + SMS | Graded Bob (16/20, 1 day late) | Email in Mailpit, and `[SMS] +261 34 00 000 02 : Votre note pour « Tri à bulles » est disponible : 14,4/20.` OK |
| SMS failure | Made the `@Bean` throw an exception, graded Clara | HTTP 200, `[SMS] échec ...` in the logs, grade 11.2 still saved. OK |
| Change provider | The failure test above | Done by editing `KilasyApplication` only; grading code untouched. OK |

The failing `@Bean` was reverted after the test.

---

### Summary

| File | Why it changed |
|---|---|
| `SubmissionController.java` | `SmsSender` interface, and the SMS sent after the email |
| `KilasyApplication.java` | simulated SMS provider (log line), the only place to change for a real provider |

---

## Change C3: timed exercises

**Tool used:** Claude Sonnet 5.5, CLI

### Exchange 1 — 00:19

> **Request:** "Explore the code and propose a solution for C3, without modifying anything. List every place that breaks if an exercise has no deadline."

**Proposal**
- `deadline` becomes nullable, plus a `duration_minutes` column, with a rule: at least one of the two.
- New table `exercise_start` in `schema.sql` to store each student's start time.
- `POST /exercises/{id}/start`: 201 on the first call, 200 afterwards with the original time, so the countdown can never be reset.
- `submit()`: 422 if the exercise was not started, or if the time is over.
- Null-deadline guards everywhere it was used (submission, grading, transcript, CSV, the 3 copies of the C1 penalty).
- Transcript: time used for timed exercises only.
- Also: start time with `DEFAULT now()` in SQL, 2 demo exercises added to `data.sql`, a 409 if the student starts after submitting.

**Decision:** kept, with 4 corrections.
**Why:**
1. **Clock bug:** Postgres runs in UTC, the app in local time (UTC+3). With `now()` in SQL, every submission would look 3 h after the start and be refused. The start time must be written by Java, with the same clock as `submitted_at`.
2. **No change to `data.sql`:** 2 new demo exercises would appear in every student's transcript. Test exercises are created through the API instead.
3. **No 409 on start after a submission:** nobody asked for it, and it changes nothing.
4. **Duration ≤ 0 refused with a 400**, instead of a 500 from the SQL constraint.

**Verification**
Checked myself: `show timezone` in Postgres gives UTC while the system clock is 3 h ahead; `compose.yaml` has no named volume, so `docker compose down` + `up` is enough to recreate the database.

---

### Exchange 2 — 00:20

> **Request:** "Implement it with these 4 corrections, then compile. Don't run the app and don't commit: I verify myself."

**Proposal**
The implementation: 6 existing files changed, no new file, `data.sql` untouched, compilation OK.

**Decision:** kept.
**Why:** after reading the whole diff, it matches the accepted proposal and the 4 corrections, with nothing extra.

**Verification**

| Criterion | Test | Result |
|---|---|---|
| Duration in addition to / instead of a deadline | Created a 30 min exercise without deadline, and a deadline + 90 min one; neither field → 400; duration 0 → 400 | OK |
| 422 after the end | Submit without start → 422; start → 201, again → 200 same time; submit in time → 201; start moved 2 h back → 422 "dépassée de 90 min", nothing saved | OK |
| Untimed exercises unchanged | Same 26 API calls before (code of `main`) and after, on a fresh database | Identical, except the new field `durationMinutes: null` in `/exercises`. OK |
| C1 penalty kept | Deadline + duration exercise, submitted 2 days late, 16/20 | 12.8 in the grading, the SMS, the transcript and the CSV. OK |
| Time used in the transcript | Start moved 12 min 30 s back | `timeUsedMinutes: 12.5`. OK |

---

### Exchange 3 — 00:29

> **Request:** "Fix exactly these 2 defects found while reviewing your code, nothing else: (1) an exercise with a deadline and a duration, started, time over but deadline not passed, shows 'À RENDRE' in the transcript although submitting is already refused; (2) a duration of 1.5 is silently saved as 1 minute."

**Proposal**
- Transcript: "NON RENDU" as soon as the deadline has passed **or** the student's own time is over, whichever comes first.
- Creation: a non-integer duration gets the same 400 as an invalid duration.

**Decision:** kept.
**Why:** both fixes are exactly the rules requested, in 2 lines, without touching anything else.

**Verification**

| Case | Result |
|---|---|
| Duration 1.5 | 400. OK |
| Deadline in 2027 + 10 min, started 1 h ago, not submitted | submit → 422, transcript → "NON RENDU". OK |
| Same exercise, never started | "À RENDRE". OK |
| The 26 API calls compared again with the code of `main`, fresh database | Identical, except `durationMinutes: null` in `/exercises`. OK |

---

### Summary

| File | Why it changed |
|---|---|
| `schema.sql` | nullable deadline, `duration_minutes`, table `exercise_start` |
| `Exercise.java` | `durationMinutes` field |
| `ExerciseController.java` | creation with a duration, `POST /exercises/{id}/start` |
| `SubmissionController.java` | 422 rules on submit, null-deadline guards |
| `GradeReport.java` | time used, null-deadline guards |
| `CsvExport.java` | empty cell instead of "null" for an exercise without deadline |
