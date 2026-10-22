# The `dev` profile grants licensed features only through a signed development key

Until 6.0, an instance running the `dev` Spring profile got `DevLicenseService`: every licensed
feature enabled, unlimited projects, no expiry. That service ships in the released image, so
`SPRING_PROFILES_ACTIVE=dev` on `yontrack/yontrack:<version>` unlocked environments, scorecard
estates, the audit trail, the native finding formats and agent governance without a licence key.
The profile is otherwise poor in production - mail, Slack, Jenkins, TFC and JIRA are mocked,
transactions are pass-through - but someone after a licensed feature alone would not mind. The
decisions are in yontrack/yontrack#2107 and, for the key format, yontrack/yontrack-license#28.

## Decisions

**The `dev` profile needs a development key.** A licence key like any other, in
`ontrack.config.license.key`, signed by the production key pair, whose signed content carries
`"usage": "dev"`. `DevLicenseService` grants what that key grants - its features, limits and end of
validity - and keeps its toggles on top: a feature can be switched off and back on, never on beyond
the key (`403`). Without a key, or with one that is not a valid development key, the instance still
starts, core-only, with a banner saying so: contributors and agents without the key keep a working
development stack.

**A production instance refuses a development key**, and a development instance grants nothing
from a production one. A key with no `usage` is a production key, so every key issued before keeps
working, byte for byte.

**Only the Yontrack development pipelines hold the key**: the `YONTRACK_DEV_LICENSE_KEY` secret of
the repository, and `~/.yontrack/dev-license.key` on the maintainer's machine. It reaches an
instance through a variable of its own - `scripts/dev-stack.sh`, `DevLicenseKey` in `buildSrc` for
the KDSL and Playwright stacks, the DAST stack - never through `ONTRACK_CONFIG_LICENSE_KEY` in a
shell, which would replace the evaluation key of the public `compose/docker-compose.yml`. It is
valid for a year; the `LICENSE.DEV` stamp warns on Slack 30 days before its end.

**The integration tests need no key.** Their contexts run `dev`; the IT fixtures
(`ITDevLicenseKeyConfiguration` in `ontrack-it-utils`) provide a base granting every feature. It is
a test fixture, so no production jar contains a way to grant the licensed features without a key.

*Rejected:* taking the dev code out of the released image. The release re-tags the very image CI
ran the acceptance tests against, in `dev`, and `dev` also carries the ~30 mocks those tests rely
on: the image released would no longer be the image tested.

*Rejected:* a runtime gate with no secret - refusing `dev` unless a variable is set, or unless CI is
detected. Whatever the binary checks without a secret is readable in the source, and copied in a
minute.

## Consequences

- A CI job which starts a `dev` stack - `kdsl`, `kdsl-ha`, `ui-tests`, `dast-active` - gets the
  secret, and fails at once without it, rather than failing every licensed test one by one.
- The key expires: renewing it means issuing a new one from the licence service, then replacing the
  secret and the local file. `DEVELOPMENT.md` says how.
- This closes the door to someone using the released image as it is. Someone rebuilding Yontrack
  from its source can remove the check; that is for the licence of the source, not for the code.
