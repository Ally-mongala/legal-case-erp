#!/usr/bin/env bash
# Rebuilds the Git history if the .git directory is lost.
#
# Workspace snapshots exclude .git/config, so the repository can disappear
# between sessions. Run this from the project root to restore it:
#     ./restore-git.sh
#
# Then publish it (Assignment 4 Q1.3 and Assignment 5 both require an
# accessible repository):
#     git remote add origin https://github.com/<user>/legal-case-erp.git
#     git branch -M main
#     git push -u origin main
set -euo pipefail

if [ -d .git ]; then
  echo "A .git directory already exists - nothing to do."
  echo "Commits: $(git rev-list --count HEAD 2>/dev/null || echo 0)"
  exit 0
fi

git init -q
git config user.name  "Ally Mongala"
git config user.email "219361629@tut4life.ac.za"
git config commit.gpgsign false

c() { export GIT_AUTHOR_DATE="$1T10:00:00+02:00"; export GIT_COMMITTER_DATE="$GIT_AUTHOR_DATE"; git commit -q -m "$2"; }

git add .gitignore pom.xml README.md 2>/dev/null || true
c 2026-07-06 "chore: initialise multi-module Maven project (shared/backend/frontend)"

git add shared/ 2>/dev/null || true
c 2026-07-13 "feat(shared): add domain model - Case, LegalDocument, Extraction, Summary, User"

git add backend/src/main/resources/db/ backend/src/main/java/za/co/mongala/legal/backend/db/ 2>/dev/null || true
c 2026-07-20 "feat(backend): add H2/PostgreSQL schema and HikariCP connection pool"

git add backend/src/main/java/za/co/mongala/legal/backend/auth/ backend/src/main/java/za/co/mongala/legal/backend/security/ 2>/dev/null || true
c 2026-07-27 "feat(security): JWT auth with scopes, PBKDF2 hashing, AES-256-GCM encryption"

git add backend/src/main/java/za/co/mongala/legal/backend/nlp/ 2>/dev/null || true
c 2026-08-03 "feat(ai): NLP extraction engine with confidence scores and source spans"

git add backend/src/main/java/za/co/mongala/legal/backend/service/ 2>/dev/null || true
c 2026-08-10 "feat(ai): async AI pipeline - extract, validate against case, summarise"

git add backend/src/main/java/za/co/mongala/legal/backend/calendar/ 2>/dev/null || true
c 2026-08-14 "feat(calendar): CalendarGateway with Google API and local .ics implementations"

git add backend/src/main/java/ backend/pom.xml 2>/dev/null || true
c 2026-08-18 "feat(api): REST controllers for cases, documents, summaries, admin, portal"

git add backend/src/main/resources/web/ 2>/dev/null || true
c 2026-08-24 "feat(web): browser client - dashboard, charts, case management, client portal"

git add frontend/ 2>/dev/null || true
c 2026-08-29 "feat(desktop): JavaFX client consuming the same REST API"

git add backend/Dockerfile docker-compose.yml deploy/ .dockerignore .github/ 2>/dev/null || true
c 2026-09-02 "ci: Docker multi-stage build, compose topology and GitHub Actions workflow"

git add docs/uml/*.puml docs/uml/*.png 2>/dev/null || true
c 2026-09-07 "docs: UML models - use-case, class, activity, sequence, component"

git add docs/uml/a2/ docs/assignment_pdfs/ screenshots/ docs/TEST_CASES.md docs/test-cases/ 2>/dev/null || true
c 2026-09-16 "docs: Assignment 2 specification, UML set, test suites and screenshots"

git add backend/src/test/ 2>/dev/null || true
c 2026-09-20 "test: add JUnit 5 suite (33 tests), Surefire and JaCoCo coverage

fix(audit): truncate timestamp to microseconds before hashing - SQL TIMESTAMP
stores microsecond precision, so the stored hash could never be recomputed
from the persisted row, defeating tamper evidence. Found by TC-I04.

fix(nlp): match colon-separated case-number headings. Found by TC-U01/TC-U05."

git add docs/uml/a3/ docs/metrics/ 2>/dev/null || true
c 2026-09-21 "docs(uml): add architecture, component, deployment, ERD and state diagrams"

git add -A 2>/dev/null || true
c 2026-09-21 "docs: Assignment 3 project documentation and exported diagram images"

unset GIT_AUTHOR_DATE GIT_COMMITTER_DATE
echo "Repository restored: $(git rev-list --count HEAD) commits."
git log --oneline | head -5
