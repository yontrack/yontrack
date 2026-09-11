import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import {Form} from "antd"
import fs from "fs"
import path from "path"

// antd's Form reads the responsive breakpoints; jsdom ships no `matchMedia`.
// Same stand-in as the other antd component tests.
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

/**
 * The mobile UI's rule-id lookup, and the thing that stops it drifting.
 *
 * The desktop UI looks a rule component up at runtime through `Dynamic`, whose
 * webpack context belongs to the Pages Router layer and therefore hands an App
 * Router screen a second React - so `/mobile` has to name its components
 * statically. That duplicated table is the cost, and these tests are what make it
 * a cheap one: a rule added for the desktop fails here rather than leaving a
 * phone silently unable to explain itself.
 */

import {
    MOBILE_RULE_DATA_FORMS,
    MOBILE_RULE_SUMMARIES,
    MobileAdmissionRuleDataForm,
    MobileAdmissionRuleSummary,
} from "@components/mobile/deployments/admissionRuleComponents"

/** Every rule the desktop UI has components for, read off disk. */
const RULES_DIR = path.join(__dirname, '../../../components/framework/environments-slot-admission-rule')

const rulesWith = (file) =>
    fs.readdirSync(RULES_DIR, {withFileTypes: true})
        .filter(entry => entry.isDirectory())
        .filter(entry => fs.existsSync(path.join(RULES_DIR, entry.name, file)))
        .map(entry => entry.name)
        .sort()

describe('the mobile admission rule components', () => {

    it('names every rule the desktop UI can phrase', () => {
        // A rule with a `Summary.js` the mobile UI does not name is an
        // environment a phone cannot explain the refusal of.
        expect(Object.keys(MOBILE_RULE_SUMMARIES).sort()).toEqual(rulesWith('Summary.js'))
    })

    it('names every rule the desktop UI can take input for', () => {
        // A rule with a `DataForm.js` the mobile UI does not name is a
        // deployment that gets stuck on a phone with nothing to tap.
        expect(Object.keys(MOBILE_RULE_DATA_FORMS).sort()).toEqual(rulesWith('DataForm.js'))
    })

    describe('the summary', () => {

        it('phrases a rule with the desktop UI\'s own words', () => {
            render(<MobileAdmissionRuleSummary rule={{
                name: 'gold', ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'},
            }}/>)
            expect(screen.getByText('GOLD')).toBeInTheDocument()
            expect(screen.getByText(/promotion is required/)).toBeInTheDocument()
        })

        it('falls back to the rule\'s own name rather than to nothing', () => {
            // Less informative than the sentence, but it names the thing that is
            // refusing - which is the minimum a user needs to go and look at it.
            render(<MobileAdmissionRuleSummary rule={{
                name: 'somethingNew', ruleId: 'not-a-rule', ruleConfig: {},
            }}/>)
            expect(screen.getByText('somethingNew')).toBeInTheDocument()
        })
    })

    describe('the data form', () => {

        it('draws the rule\'s own fields, named under its config id', () => {
            // The naming is what lets the input sheet hand the form's values
            // straight to `updatePipelineData`.
            // Inside a `Form`, because that is where the input sheet puts it -
            // a rule's fields are `Form.Item`s naming a path.
            render(
                <Form>
                    <MobileAdmissionRuleDataForm config={{
                        id: 'config-1',
                        name: 'approval',
                        ruleId: 'manual',
                        ruleConfig: {message: 'Approval message'},
                    }}/>
                </Form>
            )
            expect(screen.getByTestId('manual-approval')).toBeInTheDocument()
            expect(screen.getByText('Approval message')).toBeInTheDocument()
        })

        it('says a rule it cannot draw cannot be answered here', () => {
            // An empty sheet would read as a broken app; what is true is that
            // this rule needs the desktop version.
            render(<MobileAdmissionRuleDataForm config={{
                id: 'config-1', name: 'x', ruleId: 'not-a-rule', ruleConfig: {},
            }}/>)
            expect(screen.getByTestId('mobile-deployment-input-unsupported')).toBeInTheDocument()
        })
    })
})
