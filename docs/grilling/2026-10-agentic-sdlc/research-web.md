# Web research: delivery monitoring in the agentic SDLC

Briefing gathered on 2026-10-02 for the grilling session in `README.md`. Facts only, with
sources; items that could not be verified against a primary source are marked **[unverified]**.

## 1. The actors and their footprint

- **GitHub Copilot coding agent** is triggered by assigning an issue to Copilot, `@copilot` in a
  PR comment, or `/task`; it creates a branch and a *draft PR*, and every run is an "agent
  session" with a log reachable from the PR, the Agents panel, VS Code and the CLI. Actions
  workflows on its PRs do not run until a human clicks "Approve and run workflows" (optional
  since 2026-03-13). Commit identity per community threads: author `Copilot <copilot@github.com>`,
  committer `copilot-swe-agent[bot]` **[unverified]**. The Copilot usage-metrics API reports
  `agent_id`, `agent_name`, `session_count`, `user_initiated_interaction_count`, PRs
  created/merged/reviewed, and a per-user "has coding-agent activity" flag.
- **Agent HQ / "mission control"** (Universe 2025, public preview Feb 2026): one surface to
  assign, steer and track Claude, Codex and Copilot agents on the same task; branch controls,
  agent identity/access policies, audit logging, integrated Copilot code review, org-wide metrics.
- **GitHub Agentic Workflows (gh-aw)**, technical preview 2026-02-13: Markdown workflows compiled
  into Actions workflows run by an agent; read-only by default, writes only via pre-approved
  "safe outputs", actions SHA-pinned.
- **Claude Code** appends `Co-Authored-By: Claude <noreply@anthropic.com>`; newer builds add a
  `Claude-Session: https://claude.ai/code/session_<id>` trailer and a session-URL footer on PRs.
  **Codex**: the `chatgpt-codex-connector` GitHub App, `@codex`, trailers
  `Co-authored-by: Codex <codex@openai.com>`. **Devin**: GitHub App (9 read + 8 write scopes),
  `/devin` comments, session links, commit status checks linking to Devin Review. **Jules**:
  three authorship modes (sole author, co-author, commit as the user). **Cursor**: Bugbot reviews
  PRs, Autofix pushes commits from cloud agents.
- **What a tracker can ingest today**: bot logins and committer identities,
  `Co-Authored-By` / `Assisted-by` / `Claude-Session` trailers, PR labels, agent-posted PR
  comments, commit status checks, session URLs, branch-name prefixes, config files (`CLAUDE.md`,
  `AGENTS.md`, `.cursor/`). A census of 180M repos found bot-account lookup recovers only 3.3%
  of Claude Code commits; trailers and config files find the rest.

Sources: https://docs.github.com/en/copilot/how-tos/use-copilot-agents/cloud-agent/use-cloud-agent-on-github ·
https://github.blog/news-insights/company-news/welcome-home-agents/ ·
https://github.blog/changelog/2026-02-13-github-agentic-workflows-are-now-in-technical-preview/ ·
https://github.blog/changelog/2026-08-07-copilot-usage-metrics-api-adds-agent-app-activity/ ·
https://github.com/orgs/community/discussions/179983 ·
https://github.com/anthropics/claude-code/issues/98581 ·
https://docs.devin.ai/integrations/gh ·
https://arxiv.org/pdf/2602.09185 (AIDev dataset) ·
https://arxiv.org/pdf/2606.24429 (180M-repo census)

## 2. Provenance and attestation of AI-generated changes

- **Commit-layer convention converging on `Assisted-by:`**: the Linux kernel merged
  `coding-assistants.rst` (2025-12-23) requiring `Assisted-by: AGENT:MODEL [TOOL...]`, changed on
  2026-07-01 to `Assisted-by: LLM [TOOL...]`; "AI agents MUST NOT add Signed-off-by" — only humans
  certify the DCO; checkpatch supports it. Fedora Council adopted a similar policy (2025-10-22).
  No standards body has ratified a schema; `Co-Authored-By` is criticised for overloading a
  human-credit field.
- **Attestation layer**: in-toto Statement v1 + DSSE + Sigstore keyless signing; one
  recommendation puts AI authorship into SLSA provenance `externalParameters` under a
  vendor-namespaced key. `agentattest` (v0.2.x) defines a predicate binding `runId`, repo + base
  commit, agent identity, environment, human-approval state, evidence digests, OpenTelemetry /
  OpenInference trace references, agent-config digest, MCP server/tool identity and multi-agent
  delegation. Red Hat Emerging Tech proposes Sigstore (build time) + SPIFFE/SPIRE (runtime).
- **BOM standards**: SPDX 3.0 AI profile and CycloneDX ML-BOM describe models/datasets, not
  per-commit authorship. OpenSSF Model Signing v1.0 (Apr 2025) is model-centric.
- **Regulatory drivers**: EU AI Act Art. 50 (applies 2026-08-02) requires machine-readable
  marking of AI-generated content; generally read as content, not source code. ISO 42001 / SOC 2
  auditor guidance asks for an integrity-protected audit trail of agent activity, retention and
  on-demand evidence export **[secondary sources]**.

Sources: https://lkml.iu.edu/hypermail/linux/kernel/2603.1/07264.html ·
https://lilting.ch/en/articles/linux-kernel-ai-coding-assistant-policy ·
https://zircote.com/blog/2026/07/recording-ai-authorship-in-provenance/ ·
https://github.com/AuroraAeon/agentattest ·
https://next.redhat.com/2026/08/07/supply-chain-provenance-for-ai-agent-identity/ ·
https://openssf.org/blog/2025/04/04/launch-of-model-signing-v1-0-openssf-ai-ml-working-group-secures-the-machine-learning-supply-chain/ ·
https://digital-strategy.ec.europa.eu/en/faqs/transparency-obligations-under-article-50-ai-act ·
https://dev.to/radotsvetkov/ai-coding-compliance-for-2026-a-working-checklist-for-iso-42001-the-eu-ai-act-soc-2-and-tool-2i3

## 3. Agent identity and authorization

- **GitHub**: the cloud agent runs behind an egress firewall, never sees Actions secrets, answers
  only users with write access, its PRs' workflows need human approval. Rulesets add "Require an
  additional approval for unattributed Copilot pull requests" (commits whose author is not a
  linked account); since 2026-09-01 Copilot *can* count as an approving review (off by default)
  but cannot satisfy CODEOWNERS.
- **Identity products**: Microsoft Entra Agent ID (agent identities, Conditional Access, SPIFFE
  federation); Okta for AI Agents (GA May 2026) and Agent SSO (Aug 2026) with an MCP "Agent
  Gateway"; Auth0 for AI Agents adds Token Vault and **async authorization via CIBA** so an
  unattended agent pauses and pushes an approval to a human device.
- **SPIFFE/SPIRE** adapted for agents (minutes-long identities, agent→tool→model chains);
  Microsoft `identity-spiffe`. CSA (Sep 2026) summarises the emerging standards.
- **Human-in-the-loop deploy gates**: Actions approval, gh-aw safe outputs, Harness HITL on MCP
  actions, Auth0 CIBA, GitLab flows inheriting pipeline review gates.

Sources: https://docs.github.com/en/copilot/responsible-use/copilot-cloud-agent ·
https://github.blog/changelog/2026-03-13-optionally-skip-approval-for-copilot-coding-agent-actions-workflows/ ·
https://dev.to/pwd9000/copilot-can-now-approve-pull-requests-should-it-count-toward-your-branch-protection-2b78 ·
https://github.com/microsoft/identity-spiffe ·
https://auth0.com/blog/auth0-for-ai-agents-generally-available/ ·
https://labs.cloudsecurityalliance.org/research/csa-slack-20260916-225950-csa-styled/

## 4. Observability of agents

- **OpenTelemetry GenAI semantic conventions** (own repo since May 2026, status *Development*):
  operations `create_agent`, `invoke_agent`, `execute_tool`, `chat`…; attributes
  `gen_ai.agent.id/name/version`, `gen_ai.conversation.id`, `gen_ai.tool.call.id`,
  `gen_ai.usage.*`. Nothing links a span to a commit or build.
- **Claude Code native OTel**: metrics `claude_code.session.count`, `.commit.count`,
  `.pull_request.count`, `.cost.usage`, `.token.usage`; every record carries `session.id`,
  `user.email`, `vcs.repository.url.full`; `tool_result` events carry `vcs.ref.head.revision`
  (commit SHA) when `OTEL_LOG_TOOL_DETAILS=1`; `api_request` events carry `cost_usd`, `agent.name`,
  `skill.name`, `mcp_server.name`. The most concrete "agent session ↔ commit SHA" join today.
- **Trace platforms**: Langfuse has `release` (semver or git SHA) on traces; LangSmith arbitrary
  metadata; the join to a *build* is user-supplied. Faros AI claims to trace tokens to
  engineering outcomes from agent, gateway, SCM, CI/CD and incident telemetry.

Sources: https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-agent-spans.md ·
https://code.claude.com/docs/en/monitoring-usage ·
https://langfuse.com/docs/observability/features/releases-and-versioning ·
https://www.faros.ai/ai-productivity-paradox

## 5. Metrics and reporting

- **DORA 2024**: +25% AI adoption ≈ −1.5% throughput, −7.2% stability. **DORA 2025** (*State
  of AI-assisted Software Development*): AI positively correlated with throughput, still
  negatively with stability; 7-capability "AI Capabilities Model" (clear AI stance, healthy data
  ecosystem, AI-accessible internal data, strong version control, small batches, user-centric
  focus, quality internal platform).
- **Faros AI** (10k devs): +21% tasks, +98% PRs merged, +91% review time, +9% bugs, org-level
  DORA flat; 2026: PR size +51%, 31% more PRs merged with no review.
- **DX**: AI-authored share of merged code 22% (Q4 2025) → 51.9% (Q2 2026, preliminary).
  **LinearB 2026** (8.1M PRs): AI-generated PRs accepted 32.7% vs 84.4% human **[podcast]**;
  agentic PRs wait 5.3× longer for pickup, 2.6× larger. **Swarmia**: a PR is AI-assisted "if any
  commit was made by an AI tool or by a human active on an AI tool".
- **Detection methods**: LinearB — co-author trailers, known bot authors, agent PR comments,
  vendor APIs; Jellyfish — vendor usage APIs correlated with PR metadata; GitHub — Copilot
  metrics API. None connects to deployments or promotions.
- METR RCT (16 devs, 246 tasks): −19% speed while devs believed +20%.

Sources: https://dora.dev/insights/dora-2025-year-in-review/ ·
https://cloud.google.com/blog/products/ai-machine-learning/introducing-doras-inaugural-ai-capabilities-model ·
https://redmonk.com/rstephens/2024/11/26/dora2024/ ·
https://www.faros.ai/ai-productivity-paradox ·
https://newsletter.getdx.com/p/ai-assisted-engineering-q4-impact-report ·
https://newsletter.getdx.com/p/ai-authored-code-has-nearly-doubled ·
https://linearb.io/dev-interrupted/podcast/linearb-2026-benchmarks-ai-pr-merge-rate ·
https://linearb.helpdocs.io/article/ww1ciql9uw-linear-b-ai-insights-faq ·
https://www.swarmia.com/changelog/2025-11-20-track-ai-assisted-pull-requests/ ·
https://metr.org/blog/2025-07-10-early-2025-ai-experienced-os-dev-study/

## 6. Quality and safety gates for agent-produced changes

- **Review bots**: Copilot code review (approvals countable since Sep 2026, off by default),
  CodeRabbit, Cursor Bugbot (+Autofix), Graphite, Greptile; vendor benchmarks cite 44–82%
  injected-bug recall **[vendor-run]**.
- **Policy-as-code**: GitHub rulesets (unattributed-PR extra approval; Copilot cannot satisfy
  CODEOWNERS); gh-aw safe outputs; `agentattest` evaluates OPA/Rego over attestations; Harness
  runs agents as "governed steps".
- **Evals as CI gates**: Promptfoo, Braintrust GitHub Action — gate *LLM features*, not
  agent-written application code.
- **Security**: Veracode 2025 (100+ LLMs): insecure option chosen 45% of the time. A post-merge
  study found AI-co-authored PRs carry 10.83 issues/PR vs 6.45 human.
- **Merge/abandon numbers**: fix-related agent PRs merge 65% overall (Codex 81.6%, Copilot
  42.4%, Devin 42.9%; n=8,106), top non-merge reasons failing tests and issue already fixed;
  across 33,707 agent PRs 28.3% merge "instantly"; merge-conflict rate 27.67%; cross-agent
  concurrent PR pairs conflict 41.7% vs 19.8% intra-agent.

Sources: https://docs.github.com/en/copilot/concepts/agents/code-review ·
https://www.veracode.com/resources/analyst-reports/2025-genai-code-security-report/ ·
https://arxiv.org/html/2601.20109 · https://arxiv.org/html/2602.00164v1 ·
https://arxiv.org/html/2601.18749 · https://arxiv.org/html/2604.03551v1 ·
https://arxiv.org/pdf/2607.04697 ·
https://www.promptfoo.dev/docs/integrations/ci-cd/ ·
https://www.braintrust.dev/articles/llm-eval-pipeline-github-actions

## 7. Agents consuming delivery data

- **MCP coverage**: GitHub official MCP server (Actions toolset), Azure DevOps remote MCP (GA),
  GitLab Duo Agent Platform (external agents), Jenkins official MCP plugin, Argo CD MCP (Akuity)
  and Akuity "Agentic Control Plane" (Sep 2026), Harness MCP (RBAC, audit, HITL), Octopus Deploy
  MCP (now creates Kubernetes deployments), Backstage MCP + `AiResource` catalog entity, Port MCP
  server + MCP registry, Atlassian Rovo MCP.
- **What agents ask for**: pipeline/deployment status, failure logs, "what is deployed where",
  diffs between versions. **No product found exposing "is this build promotable?" as a tool**;
  the closest are Octopus/Harness deployment triggers behind approvals.
- **Triage/remediation**: GitLab "Fix CI/CD pipeline" flow; Jules auto-fixes CI failures (Feb
  2026); Harness Autofix; Spacelift Saturnhead explains failed Terraform runs; gh-aw's canonical
  examples are CI-failure analysis and triage.

Sources: https://github.com/github/github-mcp-server ·
https://chatforest.com/guides/mcp-cicd-platform-integrations/ ·
https://github.com/harness/mcp-server ·
https://www.harness.io/blog/harness-delivery-intelligence-now-inside-antigravity ·
https://www.globenewswire.com/news-release/2026/09/14/3361377/0/en/akuity-launches-agentic-control-plane-for-software-delivery-bringing-operational-context-and-governance-to-ai-agents.html ·
https://futurumgroup.com/insights/can-octopus-deploys-mcp-server-revolutionize-kubernetes-onboarding/ ·
https://jules.google/docs/changelog/2026-02-19/ ·
https://dev.to/saaro_net/backstage-2026-from-service-catalog-to-ai-hub-for-developer-portals-4dn

## 8. Competitors and adjacent tools positioning on AI

- **Harness**: Autonomous Worker Agents + Agent Marketplace GA (Jun 2026) as governed pipeline
  steps; AI agents as a CD deployment type; "Agent DLC" (Jul 2026).
- **Octopus Deploy**: MCP server from read-only to creating full Kubernetes deployments.
- **Argo CD / Akuity**: MCP server plus Agentic Control Plane.
- **Spacelift**: Saturnhead AI explains failed IaC runs.
- **Port**: AI-agent governance, MCP registry, skills distribution. **Backstage**: MCP access to
  catalog/templates/TechDocs; `AiResource` entity.
- **Sleuth**: DORA tracking plus "Sleuth Skills"; reported acquired late 2024 **[unverified]**.
- **Faros AI**, **Jellyfish**, **LinearB**: AI impact from vendor usage APIs and trailers.
- **Atlassian**: Rovo agents assignable to Jira issues (Feb 2026), Teamwork Graph via MCP/CLI.
  **GitLab**: Duo Agent Platform GA 2026-01-15, Agents + Flows inheriting pipeline access
  controls and review gates. **Azure DevOps**: remote MCP server GA.

Sources: https://www.harness.io/blog/shipped-in-june-2026 ·
https://www.harness.io/blog/shipped-in-july-2026 ·
https://www.devopsdigest.com/spacelift-launches-saturnhead-ai ·
https://docs.port.io/ai-agents/overview/ · https://yespress.io/sleuth ·
https://thenewstack.io/jellyfish-tracks-ai-impact-across-four-major-coding-tools/ ·
https://businesswire.com/news/home/20260224033792/en/Atlassian-Introduces-Agents-in-Jira-to-Drive-Human-AI-Collaboration-at-Enterprise-Scale ·
https://siliconangle.com/2026/05/06/atlassian-opens-teamwork-graph-pushes-rovo-agentic-execution-team-26/ ·
https://about.gitlab.com/press/releases/2026-01-15-gitlab-announces-duo-agent-platform-general-availability/

## 9. Open problems practitioners complain about

- **Review load / PR flood**: agent PRs up to 10× larger; review time +91%; pickup 5.3× slower
  for agentic PRs; 31% more PRs merged unreviewed.
- **Trust and accountability**: Stack Overflow 2025 — 84% use AI, 46% distrust accuracy, 66%
  "almost right but not quite"; kernel/Fedora pin accountability on the human submitter.
- **Loss of knowledge / "comprehension debt"**: an Anthropic study of 52 engineers found 50% vs
  67% comprehension-quiz scores; a paper argues authorship-based knowledge metrics (git blame)
  collapse.
- **Auditability**: session links reviewers cannot open, co-author trailers added without
  consent, unattributed commits blocked by rulesets.
- **Cost attribution per agent**: ~4× tokens for single agents, ~15× for multi-agent vs chat;
  Claude Code exposes `cost_usd` per `session.id` but no product ties it to a build.
- **Multi-agent coordination and stale branches**: 79.4% of agent PRs land in repos with
  concurrent agent PRs; cross-agent pairs conflict 41.7%; PRs "ghosted" after feedback.

Sources: https://stackoverflow.co/company/press/archive/stack-overflow-2025-developer-survey/ ·
https://arxiv.org/pdf/2607.07980 ·
https://www.oreilly.com/radar/comprehension-debt-the-hidden-cost-of-ai-generated-code/ ·
https://arxiv.org/html/2604.13277v1 · https://arxiv.org/pdf/2606.20882 ·
https://arxiv.org/pdf/2608.23610 ·
https://zylos.ai/research/2026-05-18-ai-agent-token-attribution-cost-allocation/ ·
https://arxiv.org/pdf/2607.04697

## Gaps found

- No vendor or standard defines a **build- or promotion-level** record of "which agent session
  produced this artefact"; the closest primitives are Claude Code's `vcs.ref.head.revision`
  event attribute, `agentattest`'s predicate, and SLSA `externalParameters`.
- No MCP tool found that answers **"is this build promotable / what changed between deployed
  versions"** from a delivery-monitoring system.
- No published figures on agent-initiated **deployments** (as opposed to PRs) being approved or
  rolled back.
