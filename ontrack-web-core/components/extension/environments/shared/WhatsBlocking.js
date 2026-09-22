import {Button, Collapse, Space, Typography} from "antd"
import {FaCheck, FaExclamationCircle, FaHandPaper} from "react-icons/fa"
import CheckIcon from "@components/common/CheckIcon"
import {isAuthorized} from "@components/common/authorizations"
import SlotAdmissionRuleSummary from "@components/extension/environments/SlotAdmissionRuleSummary"
import SlotAdmissionRuleCheck from "@components/extension/environments/SlotAdmissionRuleCheck"
import WorkflowInstanceLink from "@components/extension/workflows/WorkflowInstanceLink"
import SlotPipelineInputDialog, {
    useSlotPipelineInputDialog,
} from "@components/extension/environments/SlotPipelineInputDialog"
import SlotPipelineOverrideRuleDialog, {
    useSlotPipelineOverrideRuleDialog,
} from "@components/extension/environments/SlotPipelineOverrideRuleDialog"
import SlotPipelineOverrideWorkflowDialog, {
    useSlotPipelineOverrideWorkflowDialog,
} from "@components/extension/environments/SlotPipelineOverrideWorkflowDialog"
import TimestampText from "@components/common/TimestampText"
import {checksSummary, currentPhase, isClear, phaseItems} from "@components/extension/environments/shared/whatsBlockingModel"

/**
 * Why a deployment is not moving, and what to do about it.
 *
 * Job 3 of the redesign. The deployment page used to list every rule and every workflow of every
 * phase with equal weight, and the one that is failing had to be found among them; this list shows
 * **one phase** - by default the one the deployment is in - **failing items first**, and puts the
 * fix on the failing row itself.
 *
 * Three things follow from that and are worth stating, because each one is a decision that could
 * have gone the other way:
 *
 * - **Passed items are collapsed, not dropped.** "3 checks passed" is the reassurance a release
 *   manager wants; the three rows themselves are noise until somebody asks. They are one click away.
 * - **Unauthorised actions are hidden, not disabled.** This follows the rest of Yontrack, and the
 *   consequence is accepted: a user without the right sees *what* is blocking and no button for it.
 *   A disabled button would promise an action that will never become available to them.
 * - **The labels are the mobile deployment screen's labels** - "N of M checks passed", "Passed",
 *   "Blocking". The two UIs describe the same deployment to the same person on two devices, and
 *   two vocabularies for one state is how they drift apart.
 *
 * @param {Object} deployment The deployment, with its slot's workflows for the current phase.
 * @param {function} onChange Called after any inline fix, so the caller can ask the server again.
 * @param {boolean} actions Whether the inline fixes are offered at all - a read-only rendering (an
 *   earlier phase on the deployment page) passes false.
 * @param {string} phase Which phase to list - `CANDIDATE`, `RUNNING` or `DONE`. Left out, it is the
 *   phase the deployment is currently in, which is what the drawer always wants. The deployment
 *   page names one explicitly to draw a phase that is already over.
 * @param {string} testId Prefix of every test id below, so that a page showing several phases at
 *   once does not put the same id on two rows.
 */
export default function WhatsBlocking({
                                          deployment,
                                          onChange,
                                          actions = true,
                                          phase = undefined,
                                          testId = 'whats-blocking',
                                      }) {

    /*
     * `useSlotPipelineInputDialog` narrows to one rule through its *hook* argument, which a list
     * cannot vary per row without re-rendering first. Passing null shows every input the deployment
     * is still waiting on, which is the same set the row's "Answer" belongs to and never fewer.
     */
    const inputDialog = useSlotPipelineInputDialog(null)
    const overrideRuleDialog = useSlotPipelineOverrideRuleDialog({onSuccess: onChange})
    const overrideWorkflowDialog = useSlotPipelineOverrideWorkflowDialog({onSuccess: onChange})

    // Computed in the render body rather than kept in state: it is a function of the deployment and
    // of nothing else, and a list that is briefly empty before an effect fills it would flash
    // "nothing is blocking" over a deployment which is blocked.
    const shownPhase = phase === undefined ? currentPhase(deployment) : phase
    const items = phaseItems(deployment, shownPhase)
    const summary = checksSummary(items)
    const clear = isClear(items)

    /*
     * An overridden check passes, and is shown anyway. It is the one row whose *story* matters more
     * than its verdict - somebody decided this could go through, and folding it away behind "3
     * checks passed" would leave a green count with that decision hidden inside it.
     */
    const shown = items.filter(item => !item.ok || item.overridden)
    const passed = items.filter(item => item.ok && !item.overridden)

    const canAct = actions && isAuthorized(deployment?.slot ?? {}, 'pipeline', 'create')
    const canOverride = actions && isAuthorized(deployment?.slot ?? {}, 'pipeline', 'override')

    // Only the *current* phase can report "this deployment is finished": a named phase is being
    // shown as history, and history does not block anything by definition.
    const settled = phase === undefined && !shownPhase

    const row = (item) => (
        <div key={item.key} className="ot-whats-blocking-row" data-testid={`${testId}-${item.key}`}>
            <Space size={6} wrap>
                <CheckIcon id={`${testId}-${item.key}`} value={item.ok}/>
                {
                    item.kind === 'rule' ?
                        <SlotAdmissionRuleSummary
                            ruleId={item.rule.admissionRuleConfig.ruleId}
                            ruleConfig={item.rule.admissionRuleConfig.ruleConfig}
                        /> :
                        <WorkflowLabel item={item} testId={testId}/>
                }
                <Typography.Text type="secondary">
                    {item.ok ? 'Passed' : 'Blocking'}
                </Typography.Text>
                {
                    /* Inline fix: answer a rule waiting on somebody. */
                    item.kind === 'rule' && !item.ok && item.needsInput && canAct &&
                    <Button
                        size="small"
                        icon={<FaHandPaper color="orange"/>}
                        data-testid={`${testId}-answer-${item.rule.admissionRuleConfig.id}`}
                        onClick={() => inputDialog.start({
                            pipeline: {id: deployment.id},
                            onChange,
                        })}
                    >
                        Answer
                    </Button>
                }
                {
                    /* Inline fix: override a rule that refuses. */
                    item.kind === 'rule' && !item.ok && !item.overridden && item.canBeOverridden && canOverride &&
                    <Button
                        size="small"
                        danger
                        icon={<FaExclamationCircle/>}
                        data-testid={`${testId}-override-${item.rule.admissionRuleConfig.id}`}
                        onClick={() => overrideRuleDialog.start({pipeline: deployment, rule: item.rule})}
                    >
                        Override
                    </Button>
                }
                {
                    /* Inline fix: override a workflow that failed. */
                    item.kind === 'workflow' && !item.ok && !item.overridden && item.canBeOverridden && canOverride &&
                    <Button
                        size="small"
                        danger
                        icon={<FaExclamationCircle/>}
                        data-testid={`${testId}-override-${item.slotWorkflow.id}`}
                        onClick={() => overrideWorkflowDialog.start({
                            deployment,
                            slotWorkflow: item.slotWorkflow,
                        })}
                    >
                        Override
                    </Button>
                }
            </Space>
            {
                /*
                 * An override is an audit fact, not a state: who decided this could pass, when, and
                 * why. Hiding it would leave a green tick with no story behind it.
                 */
                item.overridden && item.override &&
                <div data-testid={`${testId}-override-detail-${item.key}`}>
                    <Typography.Text type="secondary">
                        {`Overridden by ${item.override.user} `}
                        <TimestampText value={item.override.timestamp} relative={true}/>
                        {item.override.message ? ` — ${item.override.message}` : ''}
                    </Typography.Text>
                </div>
            }
            {
                /*
                 * The rule's own account of what happened to it - who approved, what they wrote,
                 * or who is being waited on.
                 *
                 * Drawn only for the rules that *ask* somebody a question: either the deployment
                 * carries an answer to this one, or it is blocking while waiting for one. A
                 * promotion or branch-pattern rule has nothing to add beyond the summary already on
                 * the row, and drawing its `Check` there would restate the summary underneath
                 * itself.
                 */
                item.kind === 'rule' && (item.data || (!item.ok && item.needsInput)) &&
                <div data-testid={`${testId}-detail-${item.rule.admissionRuleConfig.id}`}>
                    <SlotAdmissionRuleCheck
                        check={item.rule.check}
                        ruleId={item.rule.admissionRuleConfig.ruleId}
                        ruleConfig={item.rule.admissionRuleConfig.ruleConfig}
                        ruleData={item.data}
                    />
                </div>
            }
            {
                !item.ok && item.reason &&
                <div>
                    <Typography.Text type="secondary">{item.reason}</Typography.Text>
                </div>
            }
        </div>
    )

    return (
        <div data-testid={testId}>
            {
                settled &&
                <Typography.Text type="secondary" data-testid={`${testId}-settled`}>
                    This deployment is finished — nothing is blocking it.
                </Typography.Text>
            }
            {
                !settled && items.length === 0 &&
                <Space data-testid={`${testId}-none`}>
                    <FaCheck color="green"/>
                    <Typography.Text type="secondary">Nothing is blocking this deployment.</Typography.Text>
                </Space>
            }
            {
                !settled && items.length > 0 &&
                <Space orientation="vertical" size={4} className="ot-line">
                    <Typography.Text type="secondary" data-testid={`${testId}-summary`}>
                        {summary.text}
                    </Typography.Text>
                    {
                        clear &&
                        <Space data-testid={`${testId}-clear`}>
                            <FaCheck color="green"/>
                            <Typography.Text type="secondary">Nothing is blocking this deployment.</Typography.Text>
                        </Space>
                    }
                    {shown.map(row)}
                    {
                        passed.length > 0 &&
                        <Collapse
                            ghost
                            size="small"
                            data-testid={`${testId}-passed`}
                            items={[
                                {
                                    key: 'passed',
                                    label: `${passed.length} check${passed.length > 1 ? 's' : ''} passed`,
                                    children: <Space orientation="vertical" size={4} className="ot-line">
                                        {passed.map(row)}
                                    </Space>,
                                }
                            ]}
                        />
                    }
                </Space>
            }
            {
                /*
                 * Mounted only where they can be opened. The deployment page draws this component
                 * several times over - the current phase, and each phase already gone - and three
                 * dialogs per rendering would put four fields labelled "Message" in one document,
                 * which is how a `getByLabel` ends up filling an invisible one.
                 */
                actions &&
                <>
                    <SlotPipelineInputDialog dialog={inputDialog}/>
                    <SlotPipelineOverrideRuleDialog dialog={overrideRuleDialog}/>
                    <SlotPipelineOverrideWorkflowDialog dialog={overrideWorkflowDialog}/>
                </>
            }
        </div>
    )
}

/**
 * A workflow row's name, linking to the run when there is one.
 *
 * "Open workflow" is that link rather than a button: the workflow instance page is a place, and a
 * link is how Yontrack goes to places.
 */
function WorkflowLabel({item, testId}) {
    const instance = item.slotWorkflow.slotWorkflowInstanceForPipeline
    const name = item.slotWorkflow.workflow?.name
    if (instance) {
        return <WorkflowInstanceLink
            id={`${testId}-workflow-link-${item.slotWorkflow.id}`}
            workflowInstanceId={instance.workflowInstance.id}
            name={name}
        />
    }
    return <Typography.Text data-testid={`${testId}-workflow-link-${item.slotWorkflow.id}`}>
        {name}
    </Typography.Text>
}
