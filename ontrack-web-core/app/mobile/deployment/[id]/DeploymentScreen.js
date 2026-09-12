"use client"

/**
 * One deployment, on a phone: the waiting room.
 *
 * A deployment starts as a `CANDIDATE` and stays there until every admission
 * rule of its slot is satisfied - by input somebody provides, or by an override
 * somebody justifies. This screen is that decision: what the rules are, which of
 * them pass, and what a person can do about it - answer one, override one, start
 * the deployment, complete it, cancel it.
 *
 * **Completing and cancelling are here for the abnormal path** (#1736). CI
 * finishes deployments on the normal one, which is what #1725 was right about;
 * but the path a phone is actually for is the other one - CI died, or nobody is
 * coming, and the person holding a phone is the one who has to act.
 *
 * **The presentation of the cancellation is the mitigation, not an
 * implementation detail.** The lifecycle action - Start, then Complete - is the
 * primary block button; cancelling is a separate, `danger`-coloured,
 * secondary-weight button in its own row below it. The desktop
 * `SlotPipelineStatusActions` has all four side by side, which on a phone would
 * put a destructive tap a thumb-width from the constructive one; an overflow
 * menu is a desktop pattern that hides the action from the person who came for
 * it.
 *
 * **What it still does not do.** Forcing a completion (`forcing: true`) is the
 * desktop's override-gated `ForceDeploymentCommand` with a mandatory
 * justification: it bypasses controls somebody configured and is recorded as an
 * override against the user, which is the one thing on this screen a bigger
 * screen genuinely buys something for. Overriding a blocking workflow needs
 * `SlotUpdate` *and* `SlotPipelineOverride`, a pair `PROJECT_ROLE_PIPELINES_MANAGER`
 * does not hold, so it stays on the desktop too - and so does stopping one.
 *
 * **The workflows themselves are drawn** (#1737), read-only, in their own
 * section below the checks - all three triggers whatever the status, each with
 * its run for this pipeline, and each tapping through to that run's own screen.
 * That does not supersede the blocked caption above it: the caption says *why
 * the button is off* for someone who reads one line and taps nothing, the
 * section says *what is going on*. They answer different questions at different
 * distances from the button. See `MobileDeploymentWorkflows`.
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
import {Alert, Button, Space, Typography} from "antd"
import {FaBan, FaCheck, FaExclamationTriangle, FaHandPaper, FaPlay} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {useMessageApi} from "@components/providers/MessageProvider"
import {isAuthorized} from "@components/common/authorizations"
import MobileScreen from "@components/mobile/layout/MobileScreen"
import MobileSection from "@components/mobile/layout/MobileSection"
import MobileSectionList from "@components/mobile/layout/MobileSectionList"
import MobileAsyncContent from "@components/mobile/layout/MobileAsyncContent"
import MobileDeploymentInputSheet from "@components/mobile/deployments/MobileDeploymentInputSheet"
import MobileDeploymentOverrideSheet from "@components/mobile/deployments/MobileDeploymentOverrideSheet"
import MobileDeploymentFinishSheet from "@components/mobile/deployments/MobileDeploymentFinishSheet"
import MobileDeploymentCancelSheet from "@components/mobile/deployments/MobileDeploymentCancelSheet"
import MobileDeploymentWorkflows, {gqlMobileSlotWorkflow} from "@components/mobile/deployments/MobileDeploymentWorkflows"
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

    const [starting, setStarting] = useState(false)
    const [error, setError] = useState(null)

    /** Whether the completion's confirm sheet is up, and whether it is in flight. */
    const [confirmingFinish, setConfirmingFinish] = useState(false)
    const [finishing, setFinishing] = useState(false)
    /** Whether the cancellation sheet is up. */
    const [cancelling, setCancelling] = useState(false)

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
                        # The slot's workflows, all three triggers, each with its
                        # run for this pipeline - see \`MobileDeploymentWorkflows\`.
                        # Read whatever the status: a workflow which never ran is
                        # configuration, and frequently the reason nothing ever
                        # deployed here.
                        candidateWorkflows: workflows(trigger: CANDIDATE) {
                            ...MobileSlotWorkflow
                        }
                        runningWorkflows: workflows(trigger: RUNNING) {
                            ...MobileSlotWorkflow
                        }
                        doneWorkflows: workflows(trigger: DONE) {
                            ...MobileSlotWorkflow
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
                    # The exact parallel of runAction, for the other end of the
                    # lifecycle. Null for any status but RUNNING.
                    finishAction {
                        ok
                    }
                    # Why the deployment cannot move on, in the failing check's
                    # own words. On a RUNNING deployment that is a slot workflow -
                    # "Workflow is running", "Workflow has not started",
                    # "Workflow is in error" - and never an admission rule, which
                    # gates CANDIDATE to RUNNING only.
                    errorMessage
                    # What a cancellation was for, read back exactly as the
                    # desktop reads it beside a CANCELLED pipeline.
                    lastChange {
                        message
                    }
                }
            }
            ${gqlMobileSlotWorkflow}
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

    /*
     * The status decides what is on offer, one row per state:
     *
     * | CANDIDATE | input, override, Start + Cancel |
     * | RUNNING   | Complete + Cancel               |
     * | DONE      | nothing; the caption says what happened |
     * | CANCELLED | nothing; the caption says what happened, and why |
     *
     * Cancel is offered on CANDIDATE as well as on RUNNING, which the issue title
     * does not ask for: a candidate nobody will ever approve is the more common
     * thing to want rid of, and the desktop gates cancel on "non-terminal" rather
     * than on RUNNING. Offering it on one and not the other would be a mobile-only
     * rule with nothing behind it.
     */
    const candidate = deployment?.status === 'CANDIDATE'
    const isRunning = deployment?.status === 'RUNNING'
    const unsettled = candidate || isRunning

    /*
     * Why the lifecycle action is off, in the words that fit the status.
     *
     * On a CANDIDATE the rules below ARE the reason, so the caption counts them
     * and points at the list. On a RUNNING deployment they are all green and have
     * nothing to do with it - admission rules gate CANDIDATE to RUNNING only - so
     * a "n of m checks passed" caption would point at the wrong thing entirely.
     * `errorMessage` is what is read instead: already state-aware, and the failing
     * workflow's own reason rather than a pointer to the desktop.
     */
    const runBlocked = candidate && !deployment?.runAction?.ok
    const finishBlocked = isRunning && !deployment?.finishAction?.ok

    const runDeployment = async () => {
        setStarting(true)
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
            setStarting(false)
        }
    }

    /**
     * Completing the deployment, once the confirm sheet has been answered.
     *
     * `forcing: false`, deliberately: forcing is the desktop's override-gated
     * command and is out of scope here. When a slot workflow does block the
     * completion, `finishAction.ok` is false and the button never gets tapped -
     * the phone says so in that workflow's words and stops there.
     */
    const finishDeployment = async () => {
        setFinishing(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileFinishDeployment($id: String!) {
                        finishSlotPipelineDeployment(input: {pipelineId: $id, forcing: false}) {
                            finishStatus {
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
            const payload = data?.finishSlotPipelineDeployment
            const errors = payload?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else if (payload?.finishStatus?.ok === false) {
                /*
                 * A refusal is not a failed request. "Only the last pipeline can
                 * be deployed." is checked at submit time and is invisible to
                 * `finishAction`, so a RUNNING deployment superseded by a newer
                 * pipeline on its slot shows an enabled button that then fails -
                 * and the user has to be able to read why.
                 */
                setError(payload.finishStatus.message || "This deployment cannot be completed.")
            } else {
                messageApi?.success("The deployment is done.")
                reload()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setFinishing(false)
            setConfirmingFinish(false)
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
                            /*
                             * The section is drawn for anyone who can act AND for
                             * anyone the deployment is blocked for: reading why a
                             * deployment is stuck is never gated, only doing
                             * something about it is.
                             */
                            unsettled && (canAct || runBlocked || finishBlocked) &&
                            <MobileSection
                                title={isRunning ? "Complete" : "Start"}
                                testId="mobile-deployment-actions"
                            >
                                <Space direction="vertical" size="middle" style={{width: '100%'}}>
                                    <div>
                                        {
                                            candidate && canAct &&
                                            <Button
                                                block
                                                type="primary"
                                                size="large"
                                                icon={<FaPlay/>}
                                                loading={starting}
                                                // The server's verdict, not a count this
                                                // screen adds up: a rule can be satisfied by
                                                // an override as well as by passing.
                                                disabled={!deployment.runAction?.ok}
                                                data-testid="mobile-deployment-run"
                                                onClick={runDeployment}
                                            >
                                                Start the deployment
                                            </Button>
                                        }
                                        {
                                            isRunning && canAct &&
                                            <Button
                                                block
                                                type="primary"
                                                size="large"
                                                icon={<FaCheck/>}
                                                loading={finishing}
                                                // `finishAction.ok` is not the whole gate -
                                                // "Only the last pipeline can be deployed."
                                                // is checked at submit time and is invisible
                                                // here - so the mutation's own
                                                // `finishStatus` is read as well.
                                                disabled={!deployment.finishAction?.ok}
                                                data-testid="mobile-deployment-finish"
                                                onClick={() => setConfirmingFinish(true)}
                                            >
                                                Complete the deployment
                                            </Button>
                                        }
                                        {
                                            runBlocked &&
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
                                        {
                                            finishBlocked &&
                                            <Typography.Text
                                                type="secondary"
                                                className="ot-mobile-caption"
                                                data-testid="mobile-deployment-finish-blocked"
                                            >
                                                {
                                                    deployment.errorMessage ||
                                                    "This deployment cannot be completed yet."
                                                }
                                            </Typography.Text>
                                        }
                                    </div>
                                    {
                                        /*
                                         * Its own row, below the lifecycle button
                                         * and not beside it, `danger`-coloured and
                                         * at secondary weight. That separation is
                                         * an acceptance criterion of #1736 rather
                                         * than styling: it is what answers #1725's
                                         * objection to cancelling from a phone.
                                         */
                                        canAct &&
                                        <div data-testid="mobile-deployment-cancel-row">
                                            <Button
                                                block
                                                danger
                                                size="large"
                                                icon={<FaBan/>}
                                                data-testid="mobile-deployment-cancel"
                                                onClick={() => setCancelling(true)}
                                            >
                                                Cancel the deployment
                                            </Button>
                                        </div>
                                    }
                                </Space>
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

                        {/*
                          Below Checks and above the settled caption: a workflow
                          is not something a person answers, so it sits after the
                          list whose purpose is action - and before the sentence
                          that closes the screen off.
                        */}
                        <MobileDeploymentWorkflows deployment={deployment}/>

                        {
                            /*
                             * What happened, and nothing about the desktop version:
                             * there is nothing left to go there for.
                             */
                            !unsettled &&
                            <Typography.Text
                                type="secondary"
                                className="ot-mobile-caption"
                                data-testid="mobile-deployment-settled"
                            >
                                {
                                    deployment.status === 'CANCELLED'
                                        ? "This deployment was cancelled."
                                        : "This deployment is finished."
                                }
                                {
                                    // The reason it was cancelled for, beside it as
                                    // the desktop shows it. Mandatory on the way in,
                                    // so a cancellation made here always has one.
                                    deployment.status === 'CANCELLED' && deployment.lastChange?.message &&
                                    ` ${deployment.lastChange.message}`
                                }
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
                        <MobileDeploymentFinishSheet
                            deployment={deployment}
                            open={confirmingFinish}
                            running={finishing}
                            onClose={() => setConfirmingFinish(false)}
                            onConfirm={finishDeployment}
                        />
                        <MobileDeploymentCancelSheet
                            deployment={deployment}
                            open={cancelling}
                            onClose={() => setCancelling(false)}
                            onCancelled={reload}
                        />
                    </div>
                }
            </MobileAsyncContent>
        </MobileScreen>
    )
}
