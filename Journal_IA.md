# AI Journal — Kilasy

---

## Change C1: late penalty on submissions

**Tool used:** Claude Code (Sonnet 5), CLI

### Exchange 1 — 22:51

> **Request:** "Before changing anything, explore and understand the Kilasy codebase."

**Proposal**
Read `SubmissionController`, `GradeReport`, `CsvExport` and `Exercise` to find every place that computes a grade, since the module forbids rewriting and the exact scope needed to be known first.

**Decision:** kept.
**Why:** needed to know the real scope before touching anything.

**Verification**
Found exactly 3 classes computing a /20 grade, each one independently:
- `SubmissionController` — twice: `get()` and `grade()`
- `GradeReport`
- `CsvExport`

---

### Exchange 2 — 23:17

> **Request:** "Implement the feature with every detail the client asked for: -10% per day of delay started, capped at -50%, the same grade everywhere, never negative."

**Proposal**
Add the penalty in the 4 places found above:

| Term | Value |
|---|---|
| `daysLate` | whole days between the deadline and the submission date |
| `penalty` | `min(0.5, daysLate * 0.10)` |
| `grade` | `raw grade * (1 - penalty)` |

One small private helper method inside `SubmissionController`, reused by its own `get()` and `grade()`; no shared class across the three files.

While implementing, also noticed the three files already round differently (`Math.round`, `BigDecimal` HALF_UP, `floor`) and proposed keeping each one's existing style instead of unifying them.

**Decision:** kept as proposed, including the different rounding styles.
**Why:** matches the "no reorganizing" rule exactly; unifying the rounding would have gone beyond the client's request and the scope of C1.

**Verification**
Replayed the assignment's 4 exact examples (raw grade 16 → 16 / 14.4 / 11.2 / 8) on the real demo data (Alice, Bob, Clara, David on "Tri à bulles"), then compared the three displays for each case:

| Student | Days late | Grade |
|---|---|---|
| Alice | 0 | 16 |
| Bob | 1 | 14.4 |
| Clara | 3 | 11.2 |
| David | 8 (capped) | 8 |

Identical in `GET /submissions/{id}`, `GET /students/{id}/transcript` and `GET /exports/grades.csv` for every row. OK.

---

### Summary

| File | Why it changed |
|---|---|
| `SubmissionController.java` | notation (`PUT /submissions/{id}/grade`) and submission detail (`GET /submissions/{id}`) both compute the grade here |
| `GradeReport.java` | transcript (`GET /students/{id}/transcript`) computes the grade again, separately |
| `CsvExport.java` | CSV export computes the grade a third time, separately |

No automated test added: the project has no test framework (no `src/test`, no test dependency in the pom). Verified manually with `curl` against the demo data.

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
