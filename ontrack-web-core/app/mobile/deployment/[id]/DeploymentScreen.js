"use client"

/**
 * One deployment, on a phone: the waiting room.
 *
 * A deployment starts as a `CANDIDATE` and stays there until every admission
 * rule of its slot is satisfied - by input somebody provides, or by an override
 * somebody justifies. This screen is that decision: what the rules are, which of
 * them pass, and the three things a person can do about it - answer one,
 * override one, run the deployment.
 *
 * **It stops there, deliberately.** Marking a deployment done is driven by CI
 * rather than by a person on a phone, and cancelling one is a destructive action
 * behind a thumb on a small screen; both are left to the desktop UI. The desktop
 * `SlotPipelineStatusActions` carries all four buttons side by side, which is
 * the difference between a control surface and a decision surface.
 *
 * **The rules are drawn by the desktop's own components**, reached through
 * `admissionRuleComponents` - the mobile UI's rule-id lookup, which exists
 * because the desktop's runtime one resolves into the wrong webpack layer under
 * `/mobile`. Only the layout around them is the mobile UI's own.
 *
 * **What the screen refetches, and why it refetches rather than patching.**
 * Whether a rule now passes, and whether the deployment can now run, are
 * answers only the server has: a rule can be satisfied by data another rule
 * consumes, and `runAction` is computed over all of them at once. So every
 * action bumps a counter in the query's `deps`, as the build screen's promotion
 * does - the mobile provider stack has no `EventsContextProvider`, and a phone
 * shows one screen at a time.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import Link from "next/link"
import {Alert, Button, Typography} from "antd"
import {FaExclamationTriangle, FaHandPaper, FaPlay} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {useMessageApi} from "@components/providers/MessageProvider"
import {isAuthorized} from "@components/common/authorizations"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileSection from "@components/mobile/layout/MobileSection"
import MobileSectionList from "@components/mobile/layout/MobileSectionList"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import MobileDeploymentInputSheet from "@components/mobile/deployments/MobileDeploymentInputSheet"
import MobileDeploymentOverrideSheet from "@components/mobile/deployments/MobileDeploymentOverrideSheet"
import {mobileBuildUri, mobileProjectUri} from "@components/mobile/mobileRoutes"
import {slotNameWithoutProject} from "@components/extension/environments/SlotName"
import {MobileAdmissionRuleSummary} from "@components/mobile/deployments/admissionRuleComponents"
import SlotPipelineStatusLabel from "@components/extension/environments/SlotPipelineStatusLabel"
import CheckIcon from "@components/common/CheckIcon"
import TimestampText from "@components/common/TimestampText"

export default function MobileDeploymentScreen({id}) {

    /** Bumped by every action, so the screen asks the server again. */
    const [refresh, setRefresh] = useState(0)
    const reload = () => setRefresh(count => count + 1)

    const messageApi = useMessageApi()

    /** Which rule's input sheet is up - `null` for none, `''` for all of them. */
    const [inputFor, setInputFor] = useState(null)
    /** Which rule is being overridden. */
    const [overriding, setOverriding] = useState(null)

    const [running, setRunning] = useState(false)
    const [error, setError] = useState(null)

    const query = useQuery(
        gql`
            query MobileDeployment($id: String!) {
                slotPipelineById(id: $id) {
                    id
                    status
                    start
                    build {
                        id
                        name
                        displayName
                    }
                    slot {
                        id
                        qualifier
                        environment {
                            id
                            name
                        }
                        project {
                            id
                            name
                        }
                        # What gates the actions below, read exactly as the
                        # desktop UI reads it.
                        authorizations {
                            name
                            action
                            authorized
                        }
                    }
                    # Every rule and its verdict, which is what makes this screen
                    # a decision rather than a button.
                    admissionRules {
                        canBeOverridden
                        overridden
                        check {
                            ok
                        }
                        override {
                            user
                            message
                        }
                        admissionRuleConfig {
                            id
                            name
                            description
                            ruleId
                            ruleConfig
                        }
                    }
                    # The rules still waiting on a person, with enough of their
                    # configuration for the input sheet to draw their fields.
                    requiredInputs {
                        config {
                            id
                            name
                            description
                            ruleId
                            ruleConfig
                        }
                    }
                    # The server's own answer to "can this run now?", computed
                    # over every rule at once.
                    runAction {
                        ok
                        successCount
                        totalCount
                    }
                }
            }
        `,
        {
            variables: {id},
            deps: [id, refresh],
            condition: !!id,
        }
    )

    const deployment = query.data?.slotPipelineById
    const slot = deployment?.slot
    const rules = deployment?.admissionRules ?? []

    /*
     * Which rules are still waiting on somebody, as a set of config ids. Derived
     * while rendering: it is a function of the answer that just arrived and of
     * nothing else.
     */
    const awaitingInput = new Set((deployment?.requiredInputs ?? []).map(input => input.config.id))

    /*
     * The two authorizations, off the slot's own - a user who may not act sees
     * no button rather than one that fails, as everywhere else in this UI.
     *
     * `pipeline/create` and not a right of its own: running a deployment needs
     * `SlotPipelineStart`, which every role granting `SlotPipelineCreate` also
     * grants, and which the slot does not publish as an authorization. Providing
     * input needs `SlotPipelineData`, which travels with the same two. The
     * server checks each of them for real; this only decides what is worth
     * offering.
     */
    // `?? {}` because `isAuthorized` reads `.authorizations` off what it is
    // given, and the slot is not there until the first answer lands.
    const canAct = isAuthorized(slot ?? {}, 'pipeline', 'create')
    const canOverride = isAuthorized(slot ?? {}, 'pipeline', 'override')

    /** Only a candidate is waiting for anything: the rest is CI's or the desktop's. */
    const candidate = deployment?.status === 'CANDIDATE'

    const runDeployment = async () => {
        setRunning(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileRunDeployment($id: String!) {
                        startSlotPipelineDeployment(input: {pipelineId: $id}) {
                            deploymentStatus {
                                ok
                                message
                            }
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {id: deployment.id},
            })
            const payload = data?.startSlotPipelineDeployment
            const errors = payload?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else if (payload?.deploymentStatus?.ok === false) {
                // A refused run is not a failed request: the server answers with
                // a status saying why, and the user has to be able to read it.
                setError(payload.deploymentStatus.message || "This deployment cannot be started.")
            } else {
                messageApi?.success("The deployment has started.")
                reload()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setRunning(false)
        }
    }

    return (
        <MobileScreen
            title={slot ? slotNameWithoutProject(slot) : "Deployment"}
            subtitle={
                deployment &&
                <>
                    {
                        slot?.project &&
                        <>
                            <Link href={mobileProjectUri(slot.project.id)}>{slot.project.name}</Link>
                            {' / '}
                        </>
                    }
                    {
                        deployment.build &&
                        <Link href={mobileBuildUri(deployment.build.id)}>
                            {deployment.build.displayName || deployment.build.name}
                        </Link>
                    }
                </>
            }
        >
            <MobileAsyncContent
                state={query}
                errorMessage="Could not load the deployment."
                isEmpty={query.finished && !deployment}
                empty={
                    <Alert
                        type="warning"
                        showIcon
                        message="No such deployment."
                        data-testid="mobile-deployment-missing"
                    />
                }
                rows={6}
            >
                {
                    deployment &&
                    <div className="ot-mobile-stack">
                        <Typography.Text data-testid="mobile-deployment-status">
                            <span className="ot-mobile-inline">
                                <SlotPipelineStatusLabel status={deployment.status}/>
                                <Typography.Text type="secondary" className="ot-mobile-caption">
                                    <TimestampText value={deployment.start} prefix="started" relative/>
                                </Typography.Text>
                            </span>
                        </Typography.Text>

                        {
                            error &&
                            <Alert
                                type="error"
                                showIcon
                                message={error}
                                data-testid="mobile-deployment-error"
                            />
                        }

                        {
                            candidate && canAct &&
                            <MobileSection title="Start" testId="mobile-deployment-actions">
                                <Button
                                    block
                                    type="primary"
                                    size="large"
                                    icon={<FaPlay/>}
                                    loading={running}
                                    // The server's verdict, not a count this
                                    // screen adds up: a rule can be satisfied by
                                    // an override as well as by passing.
                                    disabled={!deployment.runAction?.ok}
                                    data-testid="mobile-deployment-run"
                                    onClick={runDeployment}
                                >
                                    Start the deployment
                                </Button>
                                {
                                    !deployment.runAction?.ok &&
                                    <Typography.Text
                                        type="secondary"
                                        className="ot-mobile-caption"
                                        data-testid="mobile-deployment-run-blocked"
                                    >
                                        {
                                            `${deployment.runAction?.successCount ?? 0} of ` +
                                            `${deployment.runAction?.totalCount ?? 0} checks passed. ` +
                                            `The rest are below.`
                                        }
                                    </Typography.Text>
                                }
                            </MobileSection>
                        }

                        <MobileSectionList
                            title="Checks"
                            testId="mobile-deployment-rules"
                            isEmpty={rules.length === 0}
                            empty="This slot has no admission rule."
                        >
                            {
                                rules.map(rule => {
                                    const config = rule.admissionRuleConfig
                                    const blocked = !rule.check?.ok
                                    return (
                                        <li
                                            key={config.id}
                                            className="ot-mobile-row ot-mobile-row-stacked"
                                            data-testid={`mobile-deployment-rule-${config.id}`}
                                        >
                                            <div className="ot-mobile-row-text">
                                                <span className="ot-mobile-row-name ot-mobile-inline">
                                                    {/*
                                                      The verdict as a glyph and
                                                      as words: the state has to
                                                      survive greyscale, as the
                                                      validation chips do.
                                                    */}
                                                    <CheckIcon
                                                        id={`mobile-deployment-rule-${config.id}`}
                                                        value={rule.check?.ok}
                                                    />
                                                    <MobileAdmissionRuleSummary rule={config}/>
                                                </span>
                                                <span className="ot-mobile-row-context">
                                                    {rule.check?.ok ? 'Passed' : 'Blocking'}
                                                    {
                                                        rule.overridden &&
                                                        ` — overridden${rule.override?.user ? ` by ${rule.override.user}` : ''}`
                                                    }
                                                </span>
                                                {
                                                    rule.overridden && rule.override?.message &&
                                                    <span
                                                        className="ot-mobile-row-context"
                                                        data-testid={`mobile-deployment-override-message-${config.id}`}
                                                    >
                                                        {String(rule.override.message)}
                                                    </span>
                                                }
                                            </div>
                                            {
                                                candidate &&
                                                <div className="ot-mobile-row-actions">
                                                    {
                                                        canAct && awaitingInput.has(config.id) &&
                                                        <Button
                                                            size="large"
                                                            icon={<FaHandPaper color="orange"/>}
                                                            title="Provide the input this rule needs"
                                                            data-testid={`mobile-deployment-input-open-${config.id}`}
                                                            onClick={() => setInputFor(config.id)}
                                                        >
                                                            Answer
                                                        </Button>
                                                    }
                                                    {
                                                        /*
                                                         * A rule that passes, or
                                                         * one already overridden,
                                                         * is not overridable -
                                                         * and the same four
                                                         * conditions the desktop
                                                         * `SlotPipelineOverrideRuleButton`
                                                         * checks, in the same
                                                         * order. `canBeOverridden`
                                                         * happens to answer the
                                                         * same right as the
                                                         * slot's own
                                                         * `pipeline/override`
                                                         * today; both are read,
                                                         * because which one a
                                                         * rule-specific answer
                                                         * would arrive on is the
                                                         * per-rule one.
                                                         */
                                                        canOverride && blocked &&
                                                        !rule.overridden && rule.canBeOverridden &&
                                                        <Button
                                                            size="large"
                                                            danger
                                                            icon={<FaExclamationTriangle/>}
                                                            title="Override this rule"
                                                            data-testid={`mobile-deployment-override-open-${config.id}`}
                                                            onClick={() => setOverriding(rule)}
                                                        >
                                                            Override
                                                        </Button>
                                                    }
                                                </div>
                                            }
                                        </li>
                                    )
                                })
                            }
                        </MobileSectionList>

                        {
                            !candidate &&
                            <Typography.Text
                                type="secondary"
                                className="ot-mobile-caption"
                                data-testid="mobile-deployment-settled"
                            >
                                This deployment is no longer waiting for anything. Completing or
                                cancelling one is done from CI or from the desktop version.
                            </Typography.Text>
                        }

                        <MobileDeploymentInputSheet
                            deployment={deployment}
                            ruleConfigId={inputFor}
                            open={inputFor !== null}
                            onClose={() => setInputFor(null)}
                            onSaved={reload}
                        />
                        <MobileDeploymentOverrideSheet
                            deployment={deployment}
                            rule={overriding}
                            open={overriding !== null}
                            onClose={() => setOverriding(null)}
                            onOverridden={reload}
                        />
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}
