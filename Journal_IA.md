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
