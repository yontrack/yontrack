import {useState} from "react"
import {Alert, Button, Modal, Skeleton, Space, Tag, Typography} from "antd"
import {FaPlay} from "react-icons/fa"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {isAuthorized} from "@components/common/authorizations"
import SlotAdmissionRuleSummary from "@components/extension/environments/SlotAdmissionRuleSummary"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import {
    buildLabel,
    slotDisplayName,
    slotDisplayNameWithoutProject,
    topPromotionRun,
} from "@components/extension/environments/shared/slotCellModel"
import {
    buildChoices,
    cancellationWarning,
    slotChoices,
} from "@components/extension/environments/shared/deployDialogModel"
import {
    gqlDeployDialogBuilds,
    gqlDeployDialogSlots,
    gqlDeployDialogStart,
} from "@components/extension/environments/shared/environmentsSharedGraphQL"

/**
 * Opening the deploy dialog.
 *
 * One hook for the one way to start a deployment. `start({build})` opens it on a build and asks
 * which slot; `start({slot})` opens it on a slot and asks which build. Everything else about the
 * dialog is the same in both directions, which is the point: the redesign found four entry points
 * with four different UIs, and four different answers to "why can I not deploy this?".
 *
 * A caller which already knows *both* - a Deploy button beside one build in one slot - passes both,
 * and the dialog narrows to that single choice rather than asking a question whose answer it has
 * been given. It is still the dialog rather than a bare confirmation, because the cancellation
 * warning and the refusal reason are the whole reason this exists.
 *
 * @param {function} onSuccess Called with the new deployment's id. The drawer refreshes itself; a
 *   page-level caller navigates to the deployment.
 */
export const useDeployDialog = ({onSuccess} = {}) => {
    const [context, setContext] = useState(null)
    return {
        context,
        open: !!context,
        start: ({build, slot}) => setContext({build, slot}),
        close: () => setContext(null),
        onSuccess,
    }
}

/**
 * The one way to start a deployment.
 *
 * It replaces `BuildStartDeploymentDialog` (a `Select` whose ineligible options were `disabled`,
 * which on any device means a click that does nothing and says nothing), `SlotPipelineCreateButton`
 * (a `Popconfirm` warning that "all currently active deployments" would be cancelled, without
 * saying which or what was in them) and the project environments actions panel.
 *
 * Three things it does that none of those did:
 *
 * - **Non-eligible choices are listed with the rule that refuses them**, in the wording of the
 *   mobile deploy sheet, from `EligibleSlot.nonEligibleRules` - the rules which actually say no to
 *   *this* build, not the slot's whole rule set. A slot with three rules of which one refuses would
 *   otherwise be explained by listing all three, two of which are satisfied.
 * - **The implicit cancellation becomes an explicit warning**: *"Deployment #12 (build 105,
 *   RUNNING) will be cancelled."* That has always been what starting a deployment does; it has
 *   never been on screen.
 * - **The deploy action is hidden where the user has no right to it**, per slot, like everything
 *   else in the redesign.
 */
export default function DeployDialog({dialog}) {

    const context = dialog.context
    const fromBuild = !!context?.build
    const fromSlot = !!context?.slot && !fromBuild
    // Both known: the from-build list, narrowed to the one slot the caller named.
    const onlySlot = fromBuild ? context?.slot : null

    return (
        <Modal
            open={dialog.open}
            title={
                fromBuild ?
                    // A caller opening the dialog from a menu has the build's id and not always its
                    // name; "Deploy a build" is then the honest title rather than "Deploy build
                    // undefined".
                    (context?.build?.name ? `Deploy build ${context.build.name}` : 'Deploy a build') :
                    `Deploy to ${slotDisplayName(context?.slot)}`
            }
            onCancel={dialog.close}
            footer={<Button onClick={dialog.close} data-testid="deploy-dialog-cancel">Cancel</Button>}
            width={640}
            // Every opening asks the server again. Eligibility is a fact about this build *now* - a
            // promotion made a minute ago changes it - and a dialog remembering the previous answer
            // would offer an environment the build has since left, or hide one it has since reached.
            destroyOnClose
        >
            {fromBuild && <DeployFromBuild dialog={dialog} build={context.build} onlySlot={onlySlot}/>}
            {fromSlot && <DeployToSlot dialog={dialog} slot={context.slot}/>}
        </Modal>
    )
}

/**
 * From a build: which slot?
 */
function DeployFromBuild({dialog, build, onlySlot}) {

    const query = useQuery(
        gqlDeployDialogSlots,
        {
            variables: {buildId: Number(build?.id)},
            deps: [build?.id],
            condition: !!build?.id,
            dataFn: data => data.eligibleSlotsForBuild ?? [],
            initialData: [],
        }
    )

    const all = slotChoices(query.data ?? [])
    const choices = onlySlot ? all.filter(choice => choice.slot.id === onlySlot.id) : all

    return (
        <DeployChoices
            dialog={dialog}
            query={query}
            choices={choices}
            buildOf={() => build}
            emptyText={
                onlySlot ?
                    "This build cannot be deployed here." :
                    "This build's project has no deployment slot."
            }
            label={choice => slotDisplayNameWithoutProject(choice.slot)}
            testIdPrefix="deploy-dialog-slot"
        />
    )
}

/**
 * From a slot: which build?
 */
function DeployToSlot({dialog, slot}) {

    const query = useQuery(
        gqlDeployDialogBuilds,
        {
            variables: {slotId: slot?.id},
            deps: [slot?.id],
            condition: !!slot?.id,
            dataFn: data => data.slotById,
            initialData: null,
        }
    )

    const loaded = query.data
    // The slot the rows act on is the one that came back, because it carries the authorizations and
    // the deployment a new one would cancel; the one the caller handed in may carry neither.
    const resolvedSlot = loaded ? {...slot, ...loaded} : slot
    const choices = buildChoices(loaded?.eligibleBuilds?.pageItems ?? [], resolvedSlot)

    return (
        <DeployChoices
            dialog={dialog}
            query={query}
            choices={choices}
            buildOf={choice => choice.build}
            emptyText="No build of this project can be deployed here."
            label={choice => buildLabel(choice.build)}
            testIdPrefix="deploy-dialog-build"
        />
    )
}

/**
 * The list both modes draw, and the mutation both of them send.
 */
function DeployChoices({dialog, query, choices, buildOf, emptyText, label, testIdPrefix}) {

    const [starting, setStarting] = useState(null)
    const [error, setError] = useState(null)

    const deploy = async (choice) => {
        setStarting(choice.key)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gqlDeployDialogStart,
                variables: {
                    slotId: choice.slot.id,
                    buildId: Number(buildOf(choice).id),
                },
            })
            const errors = data?.startSlotPipeline?.errors
            if (errors && errors.length > 0) {
                // Inline rather than a toast: it is about the choice just made, and the dialog is
                // where another one can be made.
                setError(errors[0].message)
            } else {
                dialog.onSuccess?.(data?.startSlotPipeline?.pipeline?.id)
                dialog.close()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setStarting(null)
        }
    }

    if (query.error) {
        return <Alert
            type="error"
            showIcon
            message="Could not load the environments."
            description={query.error}
            data-testid="deploy-dialog-error"
        />
    }

    // `useQuery` starts with `loading` false and only flips it inside its effect, so `finished` is
    // the question: a dialog trusting `loading` alone would say "no slot" over an answer on its way.
    if (!query.finished) return <Skeleton active title={false} paragraph={{rows: 4}}/>

    if (choices.length === 0) {
        return <Typography.Text type="secondary" data-testid="deploy-dialog-empty">{emptyText}</Typography.Text>
    }

    return (
        <Space direction="vertical" size={12} className="ot-line">
            {
                error &&
                <Alert type="error" showIcon message={error} data-testid="deploy-dialog-error"/>
            }
            {
                choices.map(choice => (
                    <div key={choice.key} data-testid={`${testIdPrefix}-${choice.key}`}>
                        <Space size={8} wrap>
                            <Typography.Text strong>{label(choice)}</Typography.Text>
                            {
                                choice.build && topPromotionRun(choice.build) &&
                                <PromotionLevelImage
                                    promotionLevel={topPromotionRun(choice.build).promotionLevel}
                                    size={16}
                                />
                            }
                            {
                                !choice.eligible &&
                                <Tag color="default" data-testid={`${testIdPrefix}-ineligible-${choice.key}`}>
                                    Not eligible
                                </Tag>
                            }
                            {
                                choice.eligible && isAuthorized(choice.slot ?? {}, 'pipeline', 'create') &&
                                <Button
                                    type="primary"
                                    icon={<FaPlay/>}
                                    loading={starting === choice.key}
                                    // Only the one in flight spins; the rest go quiet, because a
                                    // second deployment started by a double click is a real one.
                                    disabled={starting !== null && starting !== choice.key}
                                    data-testid={`${testIdPrefix}-start-${choice.key}`}
                                    onClick={() => deploy(choice)}
                                >
                                    Deploy
                                </Button>
                            }
                        </Space>
                        {
                            !choice.eligible &&
                            <div data-testid={`${testIdPrefix}-reasons-${choice.key}`}>
                                {
                                    choice.nonEligibleRules.map(rule => (
                                        <div key={rule.id}>
                                            <Typography.Text type="secondary">
                                                <SlotAdmissionRuleSummary
                                                    ruleId={rule.ruleId}
                                                    ruleConfig={rule.ruleConfig}
                                                />
                                            </Typography.Text>
                                        </div>
                                    ))
                                }
                                {
                                    choice.nonEligibleRules.length === 0 &&
                                    <Typography.Text type="secondary">
                                        This build cannot be deployed here.
                                    </Typography.Text>
                                }
                            </div>
                        }
                        {
                            choice.eligible && choice.cancels &&
                            <div>
                                <Typography.Text type="warning" data-testid={`${testIdPrefix}-cancels-${choice.key}`}>
                                    {cancellationWarning(choice.slot)}
                                </Typography.Text>
                            </div>
                        }
                    </div>
                ))
            }
        </Space>
    )
}
