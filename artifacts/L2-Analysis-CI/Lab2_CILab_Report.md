# Lab 2 Report — CILab (Continuous Integration)

**Course:** Software Maintenance
**Student:** Ignas Beinoras (GitHub: IgnasBeinoras)
**Working solo** — no team coordination.
**Date:** 2026-04-28
**Local working copy:** `D:\Users\hp\Desktop\Software maintinence labs\JHotDraw`
**Course base repo:** `sweat-tek/JHotDraw`

> Per assignment: nothing was committed or pushed for this lab. The CI files
> are kept locally. They will activate automatically the moment they are
> committed and pushed to a GitHub fork.

---

## 1. Objective

Set up a minimal GitHub-Actions CI pipeline for the JHotDraw fork that:

1. Runs on every pull request (and on pushes to default branches as a safety net).
2. Builds the multi-module Maven project.
3. Runs the test suite via Maven Surefire.
4. Authenticates Maven against `https://maven.pkg.github.com/sweat-tek/MavenRepository` so the `github` repository declared in `pom.xml` can resolve shared jars from GitHub Packages.

## 2. Files produced (all local, not pushed)

| Path (relative to the JHotDraw working copy) | Purpose |
|----------------------------------------------|---------|
| `.github/workflows/maven-ci.yml` | The GitHub Actions workflow definition. |
| `.maven-settings.xml` | Maven user settings used during CI for GitHub Packages auth. |

## 3. The workflow — `.github/workflows/maven-ci.yml`

Triggers:

```yaml
on:
  pull_request:
    branches: [main, master]
  push:
    branches: [main, master]
  workflow_dispatch: {}
```

- `pull_request` is the trigger required by the lab brief ("automatically build your project for each pull request").
- `push` on default branches is added because for a solo student CI without it never runs once a branch lands. It is harmless on a fork.
- `workflow_dispatch` lets the workflow be re-run by hand from the Actions tab when debugging the pipeline.

Permissions:

```yaml
permissions:
  contents: read
  packages: read
```

The job only needs to *read* the repo and *resolve* (read) packages, so it requests minimum scope.

Job steps (`runs-on: ubuntu-latest`):

1. **`actions/checkout@v4`** — clone the PR head into the runner.
2. **`actions/setup-java@v4`** with `distribution: temurin`, `java-version: '21'`, `cache: maven`.
   - Java 21 is the current LTS that Maven supports out of the box. The pom declares `maven.compiler.source/target = 1.8`, which Java 21 still accepts (with a deprecation warning).
   - `cache: maven` caches `~/.m2/repository`, knocking the second-build wall-clock down significantly.
3. **`mvn -v`** — sanity check that the toolchain we picked is the one being used.
4. **Build (`-DskipTests=true install`)** — compile + package every module. Splitting the build from the test run gives clearer logs when the *compilation* breaks vs. when a test breaks.
5. **Tests (`mvn test`)** — Surefire runs whatever JUnit tests live under `*/src/test/java`. (At time of writing the JHotDraw fork has no tests yet; this step succeeds with "no tests to run", which is intentional — once tests are added by later labs they will be picked up automatically without changing the workflow.)
6. **Upload Surefire reports (`actions/upload-artifact@v4`)** with `if: always()` — even on a green build the reports become a downloadable artifact, which is useful for triage when the suite grows.

Maven flags used:

| Flag | Reason |
|------|--------|
| `-B` (`--batch-mode`) | suppresses interactive prompts and the download-progress carousel — required for CI logs. |
| `-ntp` (`--no-transfer-progress`) | hides per-artifact `Downloading…` lines; further trims log noise. |
| `-s .maven-settings.xml` | tells Maven to use the project-local settings file (next section) instead of `~/.m2/settings.xml`. |

## 4. GitHub Packages auth — `.maven-settings.xml`

```xml
<settings ...>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

- The `<id>github</id>` matches the `<repository><id>github</id>` declared in `pom.xml` — this is how Maven knows which credentials to attach when contacting `maven.pkg.github.com`.
- `${env.GITHUB_ACTOR}` and `${env.GITHUB_TOKEN}` resolve to environment variables we set per-step in the workflow:
  ```yaml
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  ```
  `secrets.GITHUB_TOKEN` is a short-lived token that GitHub Actions creates automatically for each workflow run — there is **no personal access token** committed to the repository.
- Following the lab brief, the file lives in the *project root* (`<YOUR_PROJECT>/.maven-settings.xml`) and is referenced via `mvn -s .maven-settings.xml`.

## 5. Verifying locally (without pushing)

Although the lab keeps the workflow local, the build steps the workflow performs were rehearsed locally to confirm the configuration is correct:

```sh
# from D:\Users\hp\Desktop\Software maintinence labs\JHotDraw
mvn -B -ntp -s .maven-settings.xml -DskipTests=true install
mvn -B -ntp -s .maven-settings.xml test
```

Set `GITHUB_ACTOR` / `GITHUB_TOKEN` (a personal access token with `read:packages` scope) before running locally, otherwise GitHub Packages will return 401 for the shared-jar resolve.

## 6. What I learned

- **CI is configuration, not code.** The "what to do" is two short YAML files plus a Maven flag. The hard part is matching IDs (`pom.xml <repository id="github">` ↔ `.maven-settings.xml <server id="github">`) and choosing the right toolchain (Java 21 + Temurin + Maven cache).
- **The default `GITHUB_TOKEN` is enough for most read flows.** A personal access token is only needed for *publishing* to Packages — which is the next lab, not this one.
- **`pull_request` trigger means PRs in a fork must target the fork's default branch** (or the repository must use the same default branch name as the trigger — `main` and `master` are both listed for safety).
- **Caching matters.** `actions/setup-java`'s `cache: maven` parameter is the cheapest performance win available for a multi-module Maven build of this size.

## 7. Files produced

| Path | Type |
|------|------|
| `Software maintinence labs/JHotDraw/.github/workflows/maven-ci.yml` | CI workflow (local — not pushed) |
| `Software maintinence labs/JHotDraw/.maven-settings.xml` | Maven settings for GitHub Packages auth (local — not pushed) |
| `Software maintinence reports/Lab2_CILab_Report.md` | This report |

Nothing was committed to the fork or pushed to GitHub. To activate the
pipeline later, commit the two JHotDraw files on a feature branch and open a
PR: the workflow will run automatically on the PR.

## References

- *Building and testing Java with Maven* — GitHub Docs.
- *Working with the Apache Maven registry* — GitHub Docs.
- Booch, G. *Object-Oriented Analysis and Design with Applications.* (CI as a 1991 method.)
- Beck, K. *Embracing Change with Extreme Programming.* 1999. (CI integrated into XP.)
