# V6 code inventory: what already exists for delivery done by AI agents

Inventory of the `v6` branch (HEAD `fb8beec372`) taken on 2026-10-02 for the grilling session
in `README.md`. Facts only; paths are relative to the repository root.

**Headline:** nothing in the product code is about AI, LLMs or MCP. The only product comment
about agents is in `ontrack-extension-scm/.../changelog/SCMCommitMessages.kt`: change logs show
only the first line of a commit message because "commit messages — those written by coding agents
in particular — routinely run to dozens of lines". The MCP server (`yontrack/yontrack-mcp`) and
the agent skills (`yontrack/yontrack-skills`) live in companion repositories.

## 1. Build identity and provenance

- `Build` (`ontrack-model/.../structure/Build.kt`): `id`, `name`, `description`,
  `signature: Signature`, `branch`. `Signature` is `time` + `user: User`; `User` is
  `data class User(val name: String)`. **No account id, token id, actor type or source on a
  signature.**
- `RunInfo` (`.../structure/RunInfo.kt`): `sourceType`, `sourceUri`, `triggerType`,
  `triggerData`, `runTime`, `signature`; attaches to builds and validation runs.
  `CreateBuildInput { branchId, branchName, description, name!, projectId, projectName, runInfo }`.
- Build properties: `release` (display name), `gitCommit`, `metaInfo` (list of
  `name, value, link, category`, searchable as `category/name:value`), `message`, CI run links
  (`BuildGitHubWorkflowRunPropertyType`, GitLab pipeline, Bitbucket pipeline, Jenkins),
  `ValidationRunGitHubWorkflowJobPropertyType` on validation runs.
- Build links: `BuildLink(build, qualifier)`; mutations `linkBuild`, `linksBuild`,
  `deleteBuildLinks`; search type `build-link`.
- ADR 0006: the rc build that is tested is the one that ships; the build name is an opaque id,
  the `release` property is rewritten once at GOLD. BRONZE = green, SILVER = verified on the
  demo, GOLD = a human approved.
- Who created a build: only `signature.user.name`. GitHub ingestion uses
  `Signature.of(workflowRun.createdAtDate, payload.sender?.login ?: "hook")`
  (`WorkflowRunIngestionEventProcessor.kt:238`). Known gap: `createBuild` overwrites the
  signature with `currentSignature` (harvesting brief).

## 2. Validation stamps, runs, data types; findings

- Statuses (`ValidationRunStatusID.kt`): PASSED, WARNING, FAILED, DEFECTIVE, EXPLAINED, FIXED,
  INTERRUPTED, INVESTIGATING. Each status carries `signature`, `statusID`, `description`.
- Data types: `tests`, `chml`, `metrics`, `number`, `percentage`, `security-findings`, plus
  fraction, text, CHM and auto-versioning types.
- Typed mutations `validateBuildWith{CHML,Metrics,Number,Percentage,Tests,Findings}`,
  `createValidationRun[ById|ByRelease]`, `changeValidationRunStatus`.
- Findings (`ontrack-extension-findings`): `Finding(projectId, scanner, externalId, location,
  kind, title, url, firstSeen, lastSeen, resolvedAt, maxSeverity)`,
  `FindingObservation(findingId, validationRunId, time, severity, …, acceptance)`; kinds IMAGE,
  CODE, SECRETS, DAST, DEPENDENCIES, OTHER; parsers neutral, SARIF, Trivy; single door
  `validateBuildWithFindings`; events `security_finding_new|resolved`.

## 3. Promotions, checks, scorecard

- `PromotionRun(build, promotionLevel, signature, description, fieldValues)`. Promotion level
  fields are typed (BOOLEAN, CHOICE, LINK, NUMBER, TEXT), captured at promotion time.
- Auto-promotion: `AutoPromotionProperty(validationStamps, include, exclude, promotionLevels,
  autoRevoke)`; event `auto_promotion_revoked`.
- Checks: `PromotionRunCheckExtension`, two implementations (`PreviousPromotionCondition`,
  `PromotionRunDependencies`). **No bypass.**
- Scorecard: `Reading(estateId?, projectId, key, day, …, value, basis, unknownReason)`; basis
  MEASURED / ESTIMATED / UNKNOWN; keys `delivery.leadTime|frequency|successRate|mttr`,
  `quality.testPassRate|testFlakiness`; marker = promotion levels or an environment slot;
  estates selected by labels; computed daily.

## 4. Change logs

- `SCMChangeLogService.getChangeLog(from, to, dependencies, …)` → `SCMChangeLog(from, to,
  fromCommit, toCommit, commits, issues)`, computed on the fly from the SCM, can follow build
  links recursively.
- Issue keys from `issueReferenceText(commit.message)`: subject + trailer lines
  (close/fix/resolve/ref/refs/references/related/issue/issues/jira-ticket).
- `SCMCommit(id, shortId, author, authorEmail, timestamp, message, link)`. **No
  committer/author split, no co-author parsing, no bot detection.** `Co-Authored-By` appears only
  in test fixtures.
- SCMs: GitHub, GitLab, Bitbucket Server, Bitbucket Cloud. Issue services: GitHub, GitLab, Jira.
- Semantic change log: `SemanticCommit(type, scope, subject)`; untyped commits dropped (ADR 0019).

## 5. Events, notifications, recordings, workflows, templating

- `Event(eventType, signature?, entities, extraEntities, ref, values)`.
- Event types: `EventFactory.kt` (new_build, new_promotion_run, new_validation_run,
  new_validation_run_status, property_change, auto_promotion_revoked, …),
  `EnvironmentsEvents.kt` (`slot-pipeline-*`), `FindingsEvents.kt`, `AutoVersioningEvents.kt`,
  `WorkflowEvents.kt`.
- Channels: mail, slack, webhook, workflow, jira-creation, jira-link, jira-service-desk,
  github-workflow, gitlab-pipeline, bitbucket-pipelines, jenkins, yontrack-promotion,
  ontrack-validation, in-memory.
- `EventSubscription(projectEntity, name, events, keywords, channel, channelConfig, disabled,
  origin, contentTemplate)`; `NotificationRecord(id, source, timestamp, channel, channelConfig,
  event, result)`.
- Recordings: generic `RecordingsExtension`, used by hook and queue records.
- Workflows: `WorkflowNode(id, description, executorId, data, parents, timeout, interval)`;
  executors notification, pause, auto-versioning, slot-pipeline-{creation,deploying,deployed}.
- Templating functions: datetime, lastPromotion, link, pipeline, since, slot, user.

## 6. Environments, slots, deployments

- `Slot(environment, project, qualifier, description)`; `SlotPipeline(id, number, start, end,
  status, slot, build)`; status CANDIDATE, RUNNING, DONE, FAILED, CANCELLED.
- `SlotPipelineChange(user, timestamp, type STATUS|RULE_DATA|RULE_OVERRIDDEN|WORKFLOW_OVERRIDDEN,
  status, message, overrideMessage)` and `SlotAdmissionRuleOverride(user, timestamp, message)`:
  **the most complete "who did what" trail in the core model.**
- Admission rules: `promotion`, `branchPattern`, `environment`, `manual`
  (`ManualApprovalSlotAdmissionRuleConfig(message, users, groups)`, checked against
  `currentSignature.user.name` and the user's groups).
- Mutations `startSlotPipeline`, `startSlotPipelineDeployment`, `finishSlotPipelineDeployment`,
  `failSlotPipeline`, `cancelSlotPipeline`, `overridePipelineRule`, `overridePipelineWorkflow`,
  `updatePipelineData`. Fine-grained functions `SlotPipelineStart|Finish|Override|Data`.

## 7. Hooks and ingestion

- `POST /hook/secured/{hook}` via `HookEndpointExtension` (only TFC implements it); HMAC
  `HookSignature`; `HookRecord` recordings; queue dispatch.
- GitHub ingestion: `POST /hook/secured/github/ingestion`; processors ping, push, pull_request,
  workflow_run, workflow_job; `gitHubIngestionValidateDataBy{RunId,BuildName,BuildLabel}`.
- Queue: `QueueProcessor`, `QueueRecord` audit; no retry or dead letter.
- CI config injection (`ontrack-extension-config`, licensed): `.yontrack/ci.yaml` applied by
  `configureBuild` / `configureBranch`; engines GitHub, GitLab, Jenkins, Bitbucket, Generic.

## 8. Security model, identity, audit

- Project roles OWNER, PARTICIPANT, VALIDATION_MANAGER, PROMOTER, PROJECT_MANAGER, READ_ONLY;
  global roles ADMINISTRATOR, CREATOR, AUTOMATION, CONTROLLER, PARTICIPANT, READ_ONLY,
  GLOBAL_VALIDATION_MANAGER.
- AUTOMATION ("users or groups which must automate Ontrack") grants project creation, group
  management, and build/promotion/validation creation on every project.
- `Account(id, fullName, email, role ADMINISTRATOR|USER)`, groups, `GroupMapping` from the IdP.
  OIDC only (LDAP removed in `V60`). Tokens: `Token(name, value, creation, validUntil,
  lastUsed)`, header `X-Ontrack-Token`, mutations `generateToken`, `revokeToken`, ….
- **No service-account, bot or actor-type notion anywhere. A token always belongs to a
  person's account.**
- Audit trails: `EntityDataStore.getRecordAudit`, `AutoVersioningAuditStore`,
  `SlotPipelineChange`, signatures on build / promotion run / validation run status, hook,
  queue and notification records.

## 9. Programmatic surfaces

- GraphQL snapshot `ontrack-web-core/ontrack.graphql`: ~13k lines, 588 types, ~320 mutations.
- REST: ~60 controllers (`BuildController`, `RunInfoController`, `SearchController`,
  `CascController`, `HookController`, `IngestionHookController`, `TokensController`, …).
- KDSL: 37 core spec files plus one per extension. CLI: Go binary in `yontrack/yontrack-cli`.
- CasC: ~23 contexts. Search: Postgres-based (ADR 0017); result types project, branch, build,
  build-release, build-link, scm-commit, scm-issue, git-branch, scm-catalog, finding.

## 10. Current strategic direction (recent grilling sessions)

- **Ledger (6.1, `2026-09-ledger.md`)**: licensed `ontrack-extension-ledger`; a JSON Lines
  stream of facts with a header (`schemaVersion`, `producer`, `source`, `createdAt`) and a fact
  envelope (`id`, `kind`, `time`, `basis` MEASURED|ESTIMATED, `evidence`, `payload`); thirteen
  fact kinds (project, branch, promotionLevel, validationStamp, environment, slot, build,
  promotion, validation, runInfo, link, deployment, property); global function `LedgerImport`;
  quiet import (no events, signatures kept, promotion checks skipped); one `ledger_imported`
  event per run; fact table as provenance record; `ledger:<source>` label; `POST
  /extension/ledger/upload`; CLI `yontrack ledger import|validate`.
- **Harvester (`2026-09-harvester.md`)**: private Go repo `yontrack/yontrack-harvester`,
  one-shot `fetch` / `collect`; collectors git, Jenkins, Bitbucket; messy human sources are mined
  by an agent into a reviewed facts file marked ESTIMATED — **"agents produce configuration and
  rules, never numbers"**; agent-neutral playbook (`AGENTS.md` + procedures); the harvester never
  holds a model key; the signature user is the producer.
- **Harvesting brief**: a portable ledger owned by Yontrack ("history-as-code"); readings are
  derived, never pushed; "unknown" is a first-class value; hazard: backfilling would fire years
  of events.
- **Delivery vision (`2026-08-delivery.md`)**: any BRONZE build can deploy to a demo; smoke tests
  promote to SILVER; a human promotes to GOLD; GOLD triggers the public release without rebuild.
- **Findings (6.0)**: own module; one synchronous door; no secret value ever stored.
- **Scorecard**: 6.x is "the release where Yontrack reads its own history"; readings materialised
  daily; basis MEASURED / ESTIMATED / UNKNOWN; estates licensed.
- `CONTEXT.md` defines ~50 terms; Ledger, Fact and Import run are decided but not yet added.

## 11. How this repository is itself developed with agents

- Issue lifecycle `status:tospec → todo → wip → ready → released` driven by agents with `gh`;
  `ready-for-agent` marks an issue specified for an unattended agent; `status:ready` only after
  a green `ci.yml` run matched on the pushed SHA.
- Specs come from grilling sessions; skills cover fix-issue, run-initiative, release-milestone,
  scaffolding. Agents run in parallel on isolated stacks (ADRs 0004, 0012–0014) and mint their
  own API tokens (`docs/agents/local-api-access.md`).
- About 209 of the 623 commits since 2026-08-01 carry a `Co-Authored-By: Claude …` trailer.
