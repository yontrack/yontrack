"use client"

/**
 * Which component draws which admission rule, for the mobile UI.
 *
 * **Why this exists at all, rather than `Dynamic`.** The desktop UI looks a rule
 * component up at runtime - `SlotAdmissionRuleSummary` and
 * `SlotAdmissionRuleDataForm` both go through `Dynamic`, which is
 * `lazy(() => import(`../${path}`))`. That template literal makes webpack build a
 * *context module* over `components/`, and the context it builds belongs to the
 * **Pages Router** layer. `/mobile` is an App Router root, which Next compiles in
 * a separate layer with its own React copy, so a component pulled in through that
 * context renders against a React whose dispatcher is null:
 *
 *     TypeError: Cannot read properties of null (reading 'useMemo')
 *         at Text (antd/lib/typography/Text.js)
 *
 * - and the `ErrorBoundary` inside `Dynamic` catches it and draws "Error" where
 * the reason for a refusal should be. Nothing about the rule, the data or the
 * screen is wrong; the module simply arrives from the wrong compilation.
 *
 * `Dynamic` is therefore **unusable from anything under `/mobile`**, and that is
 * true of every one of its uses - properties, widgets, post-processing - not only
 * of these. Any mobile screen that needs a component chosen at runtime needs a
 * static map like this one.
 *
 * **What is still shared, and what is not.** The components are the desktop's
 * own, imported from the same files `Dynamic` would have resolved: how a rule is
 * phrased and which fields it asks for stay in one place. What is duplicated is
 * only the *lookup table*, and only because a static import is the one thing that
 * resolves in the right layer.
 *
 * **A duplicated table drifts**, which is the whole reason the promote sheet
 * shares its field mapping rather than copying it. So it is pinned:
 * `admissionRuleComponents.test.js` reads
 * `components/framework/environments-slot-admission-rule/` off disk and fails
 * when a rule there has a `Summary.js` or a `DataForm.js` this file does not
 * name. A rule type added for the desktop becomes a failing test rather than a
 * phone that silently cannot explain itself.
 *
 * Both lookups also fall back rather than rendering nothing, so a rule this file
 * has never heard of degrades to something a user can still act on.
 */

import {Typography} from "antd"
import BranchPatternSummary from "@components/framework/environments-slot-admission-rule/branchPattern/Summary"
import EnvironmentSummary from "@components/framework/environments-slot-admission-rule/environment/Summary"
import ManualSummary from "@components/framework/environments-slot-admission-rule/manual/Summary"
import PromotionSummary from "@components/framework/environments-slot-admission-rule/promotion/Summary"
import ManualDataForm from "@components/framework/environments-slot-admission-rule/manual/DataForm"

/** Rule id to the component phrasing it - "GOLD promotion is required". */
export const MOBILE_RULE_SUMMARIES = {
    branchPattern: BranchPatternSummary,
    environment: EnvironmentSummary,
    manual: ManualSummary,
    promotion: PromotionSummary,
}

/** Rule id to the component drawing the input it wants. Only rules that need one. */
export const MOBILE_RULE_DATA_FORMS = {
    manual: ManualDataForm,
}

/**
 * What a rule says, in one line.
 *
 * @param {Object} rule A `SlotAdmissionRuleConfig`: its `ruleId`, its
 *   `ruleConfig` and its `name`.
 */
export function MobileAdmissionRuleSummary({rule}) {
    const Summary = MOBILE_RULE_SUMMARIES[rule?.ruleId]
    // The rule's own configured name is the fallback: less informative than the
    // sentence, but it names the thing that is refusing, which is the minimum a
    // user needs to go and look at it.
    if (!Summary) return <Typography.Text>{rule?.name ?? rule?.ruleId}</Typography.Text>
    return <Summary {...(rule.ruleConfig ?? {})}/>
}

/**
 * The fields a rule wants, named under its config id - which is the shape
 * `updatePipelineData` takes, and the reason the mobile input sheet hands the
 * form's values straight to it.
 *
 * @param {Object} config A `SlotAdmissionRuleConfig`.
 */
export function MobileAdmissionRuleDataForm({config}) {
    const DataForm = MOBILE_RULE_DATA_FORMS[config?.ruleId]
    if (!DataForm) {
        // Said out loud rather than drawn as an empty form: a user staring at a
        // sheet with nothing in it would conclude the app is broken, when what
        // is true is that this rule cannot be answered from a phone yet.
        return (
            <Typography.Text type="warning" data-testid="mobile-deployment-input-unsupported">
                This rule cannot be answered from a phone. Use the desktop version.
            </Typography.Text>
        )
    }
    return <DataForm configId={config.id} {...(config.ruleConfig ?? {})}/>
}
