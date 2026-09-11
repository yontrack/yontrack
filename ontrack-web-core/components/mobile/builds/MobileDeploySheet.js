"use client"

/**
 * Starting a deployment of a build, from a phone.
 *
 * **A bottom sheet, not the desktop dialog.** `BuildStartDeploymentDialog` is a
 * modal built around a `Select` of slots, with the chosen slot's details and its
 * current pipeline underneath; it also still reads the server through the
 * deprecated `useGraphQLClient`. Both halves of the boundary in
 * `doc/dev-guide/ui/mobile-ui.md` apply: the layout is the mobile UI's own, and
 * the read and the write go through `@components/services/GraphQL`.
 *
 * **A list, not a dropdown.** The desktop control is a `Select` whose ineligible
 * entries are `disabled` options - which on a phone means a tap that does
 * nothing and says nothing. Here every slot is a card carrying its own action or
 * its own explanation, so "why can I not deploy to production?" is answered
 * where the question is asked. That is the issue's own requirement: ineligible
 * slots are shown *with the reason* rather than hidden, so a user is never left
 * wondering where an environment went.
 *
 * **The reason comes from the server**, as `EligibleSlot.nonEligibleRules` - the
 * slot's admission rules which actually refuse this build, not all of its rules.
 * A slot with three rules of which one refuses would otherwise be explained by
 * listing all three, two of which are satisfied.
 *
 * **The rule is drawn by the shared summary component**, `SlotAdmissionRuleSummary`,
 * which maps a rule id onto the component that phrases it - "GOLD promotion is
 * required". It is the same kind of sharing the promote sheet does with the
 * promotion level field mapping (#1724): the *mapping* is shared so a rule type
 * added on the desktop is phrased here too, and only the layout around it is the
 * mobile UI's own. A second copy of that mapping would drift silently, and the
 * mobile half would fall back to naming a rule the user cannot interpret.
 *
 * @param {Object} build The build being deployed - its `id` is the whole input.
 * @param {boolean} open Whether the sheet is up.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onStarted Called with the new deployment's id once the
 *   server has created it, so the screen behind can take the user to it.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Alert, Button, Drawer, Skeleton, Tag, Typography} from "antd"
import {FaPlay, FaServer} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {slotNameWithoutProject} from "@components/extension/environments/SlotName"
import SlotAdmissionRuleSummary from "@components/extension/environments/SlotAdmissionRuleSummary"
import MobileEmpty from "@components/mobile/layout/MobileEmpty"

export default function MobileDeploySheet({build, open, onClose, onStarted}) {
    return (
        <Drawer
            placement="bottom"
            // As tall as it needs to be and never taller than the phone, capped
            // in both halves - see `MobilePromoteSheet` for why the wrapper
            // alone is not enough.
            height="auto"
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Deploy build"
            open={open}
            onClose={onClose}
            // Every opening asks the server again. Eligibility is a fact about
            // this build *now* - a promotion made a minute ago changes it - and a
            // sheet remembering the previous answer would offer an environment
            // the build has since left, or hide one it has since reached.
            destroyOnClose
        >
            <MobileDeployList build={build} onClose={onClose} onStarted={onStarted}/>
        </Drawer>
    )
}

/**
 * The slots themselves, mounted only while the sheet is open.
 *
 * Separate from the sheet so the query below belongs to one opening of it: a
 * build screen mounts this for every build a user looks at, and a phone should
 * not pay for a sheet nobody opened.
 */
function MobileDeployList({build, onClose, onStarted}) {

    /** Which slot's deployment is in flight, so only its own button spins. */
    const [starting, setStarting] = useState(null)
    const [error, setError] = useState(null)

    const query = useQuery(
        gql`
            query MobileEligibleSlots($buildId: Int!) {
                eligibleSlotsForBuild(buildId: $buildId) {
                    eligible
                    # Why this slot refuses, when it does - the rules which
                    # actually say no, and not the slot's whole rule set.
                    nonEligibleRules {
                        id
                        name
                        ruleId
                        ruleConfig
                    }
                    slot {
                        id
                        qualifier
                        environment {
                            id
                            name
                            order
                        }
                    }
                }
            }
        `,
        {
            variables: {buildId: Number(build?.id)},
            deps: [build?.id],
            condition: !!build?.id,
            dataFn: data => data.eligibleSlotsForBuild ?? [],
            initialData: [],
        }
    )

    /*
     * Derived while rendering rather than kept in state: the order is a function
     * of the answer and of nothing else.
     *
     * The environment's own order first - the way a delivery pipeline actually
     * runs, staging before production - then the qualifier, so two slots of one
     * project in the same environment come out in a stable order rather than in
     * whatever order the repository happened to return them.
     */
    const eligibleSlots = [...(query.data ?? [])].sort((a, b) =>
        (a.slot?.environment?.order ?? 0) - (b.slot?.environment?.order ?? 0) ||
        (a.slot?.qualifier ?? '').localeCompare(b.slot?.qualifier ?? '')
    )

    const startDeployment = async (slot) => {
        setStarting(slot.id)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobileStartDeployment($slotId: String!, $buildId: Int!) {
                        startSlotPipeline(input: {slotId: $slotId, buildId: $buildId}) {
                            pipeline {
                                id
                            }
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {slotId: slot.id, buildId: Number(build.id)},
            })
            const errors = data?.startSlotPipeline?.errors
            if (errors && errors.length > 0) {
                // Inline rather than as a toast: it is about the slot the user
                // just tapped, and the sheet is where they can pick another.
                setError(errors[0].message)
            } else {
                onStarted?.(data?.startSlotPipeline?.pipeline?.id)
                onClose?.()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setStarting(null)
        }
    }

    if (query.error) {
        return (
            <Alert
                type="error"
                showIcon
                message="Could not load the environments."
                description={query.error}
                data-testid="mobile-deploy-error"
            />
        )
    }

    // Nothing has ever arrived. `useQuery` starts with `loading` false and only
    // flips it inside its effect, so `finished` is the question - see
    // `MobileAsyncContent`, which this deliberately does not use: it renders a
    // screen's body, and this is a sheet's.
    if (!query.finished) return <Skeleton active title={false} paragraph={{rows: 4}}/>

    if (eligibleSlots.length === 0) {
        return (
            <MobileEmpty
                testId="mobile-deploy-none"
                description="This build's project has no deployment slot."
            />
        )
    }

    return (
        <>
            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    message={error}
                    data-testid="mobile-deploy-error"
                    style={{marginBottom: 16}}
                />
            }
            <ul className="ot-mobile-cards" data-testid="mobile-deploy-slots">
                {
                    eligibleSlots.map(({eligible, nonEligibleRules, slot}) => (
                        <li
                            key={slot.id}
                            className="ot-mobile-card"
                            data-testid={`mobile-deploy-slot-${slot.id}`}
                        >
                            <div className="ot-mobile-card-head">
                                <span className="ot-mobile-card-title ot-mobile-inline">
                                    <FaServer aria-hidden="true"/>
                                    {/*
                                      Environment *and* qualifier, through the
                                      desktop UI's own formatter: a project can
                                      have two slots in one environment and
                                      nothing else tells them apart.
                                    */}
                                    {slotNameWithoutProject(slot)}
                                </span>
                                {
                                    !eligible &&
                                    <Tag color="default" data-testid={`mobile-deploy-ineligible-${slot.id}`}>
                                        Not eligible
                                    </Tag>
                                }
                            </div>
                            {
                                eligible ?
                                    <Button
                                        block
                                        type="primary"
                                        size="large"
                                        icon={<FaPlay/>}
                                        loading={starting === slot.id}
                                        // Only the one in flight spins; the rest
                                        // go quiet, because a second deployment
                                        // started by a double tap is a real one.
                                        disabled={starting !== null && starting !== slot.id}
                                        data-testid={`mobile-deploy-start-${slot.id}`}
                                        onClick={() => startDeployment(slot)}
                                    >
                                        Deploy here
                                    </Button> :
                                    <div className="ot-mobile-reasons">
                                        {
                                            /*
                                             * The rules which refuse, phrased by
                                             * the same components the desktop
                                             * pipeline page uses. A rule with no
                                             * summary of its own falls back to
                                             * nothing readable, so its name is
                                             * carried beside it.
                                             */
                                            nonEligibleRules?.map(rule => (
                                                <Typography.Text
                                                    key={rule.id}
                                                    type="secondary"
                                                    className="ot-mobile-caption"
                                                    data-testid={`mobile-deploy-reason-${rule.id}`}
                                                >
                                                    <SlotAdmissionRuleSummary
                                                        ruleId={rule.ruleId}
                                                        ruleConfig={rule.ruleConfig}
                                                    />
                                                </Typography.Text>
                                            ))
                                        }
                                        {
                                            (!nonEligibleRules || nonEligibleRules.length === 0) &&
                                            <Typography.Text type="secondary" className="ot-mobile-caption">
                                                This build cannot be deployed here.
                                            </Typography.Text>
                                        }
                                    </div>
                            }
                        </li>
                    ))
                }
            </ul>
            <Button
                block
                size="large"
                style={{marginTop: 16}}
                onClick={onClose}
                data-testid="mobile-deploy-cancel"
            >
                Cancel
            </Button>
        </>
    )
}
