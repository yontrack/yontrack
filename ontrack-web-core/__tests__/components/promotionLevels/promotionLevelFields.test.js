import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import {
    orderedPromotionLevelFields,
    promotionLevelFieldInput,
    promotionLevelFieldRules,
    promotionLevelFieldValuePropName,
    toPromotionRunFieldValues,
} from "@components/promotionLevels/promotionLevelFields"

/**
 * The one mapping from a promotion level's declared fields to a form, shared by
 * the desktop dialog and the mobile sheet.
 *
 * It is shared precisely so that a field type added on the desktop cannot
 * silently fail to render on a phone, so these tests are about the mapping
 * itself - not about either layout.
 */

const field = ({
                   name = 'ticket',
                   displayName = 'Ticket',
                   type = 'TEXT',
                   required = false,
                   options = [],
                   position = 0,
               } = {}) => ({name, displayName, description: null, type, required, options, position})

describe('the promotion level field mapping', () => {

    describe('the input a field type gets', () => {

        it('gives TEXT a text box', () => {
            render(promotionLevelFieldInput(field({type: 'TEXT'})))
            expect(screen.getByRole('textbox')).toBeInTheDocument()
        })

        it('gives NUMBER a numeric input', () => {
            render(promotionLevelFieldInput(field({type: 'NUMBER'})))
            expect(screen.getByRole('spinbutton')).toBeInTheDocument()
        })

        it('gives BOOLEAN a checkbox', () => {
            render(promotionLevelFieldInput(field({type: 'BOOLEAN'})))
            expect(screen.getByRole('checkbox')).toBeInTheDocument()
        })

        it('gives CHOICE a list of its own options', () => {
            render(promotionLevelFieldInput(field({type: 'CHOICE', options: ['prod', 'staging']})))
            expect(screen.getByRole('combobox')).toBeInTheDocument()
        })

        it('gives LINK a box that says what a link looks like', () => {
            render(promotionLevelFieldInput(field({type: 'LINK'})))
            expect(screen.getByRole('textbox')).toHaveAttribute('placeholder', 'https://...')
        })

        it('still renders something for a type it has never heard of', () => {
            // The whole reason this mapping is shared: a type added on the
            // server must not leave a required field unfillable. A text box is
            // the honest fallback - a JSON value is a string until proven
            // otherwise - and the field stays usable until the type is mapped.
            render(promotionLevelFieldInput(field({type: 'SOMETHING_NEW'})))
            expect(screen.getByRole('textbox')).toBeInTheDocument()
        })
    })

    describe('how a form holds the value', () => {

        it('binds a checkbox by its checked state', () => {
            expect(promotionLevelFieldValuePropName(field({type: 'BOOLEAN'}))).toEqual('checked')
        })

        it('binds every other type by its value', () => {
            for (const type of ['TEXT', 'NUMBER', 'CHOICE', 'LINK']) {
                expect(promotionLevelFieldValuePropName(field({type}))).toEqual('value')
            }
        })
    })

    describe('required fields', () => {

        it('names the field in the message, not the key', () => {
            // `ticket` is the storage key; `Ticket` is what the user was shown.
            const [rule] = promotionLevelFieldRules(field({required: true}))
            expect(rule.required).toBe(true)
            expect(rule.message).toEqual('Ticket is required.')
        })

        it('leaves an optional field unconstrained', () => {
            expect(promotionLevelFieldRules(field({required: false}))).toEqual([])
        })
    })

    describe('the order fields are shown in', () => {

        it('follows the declared position rather than the order they arrived in', () => {
            const fields = [
                field({name: 'second', position: 1}),
                field({name: 'first', position: 0}),
            ]
            expect(orderedPromotionLevelFields(fields).map(f => f.name)).toEqual(['first', 'second'])
        })

        it('answers with an empty list for a level that declares none', () => {
            expect(orderedPromotionLevelFields(undefined)).toEqual([])
        })

        it('does not reorder the caller\'s own array', () => {
            const fields = [field({name: 'second', position: 1}), field({name: 'first', position: 0})]
            orderedPromotionLevelFields(fields)
            expect(fields.map(f => f.name)).toEqual(['second', 'first'])
        })
    })

    describe('collecting the values for the mutation', () => {

        it('turns the form values into name/value pairs', () => {
            expect(toPromotionRunFieldValues({ticket: 'PROJ-42', count: 3}))
                .toEqual([{name: 'ticket', value: 'PROJ-42'}, {name: 'count', value: 3}])
        })

        it('keeps a false as an answer rather than as an absence', () => {
            // `value` is a JSON scalar and the server type-checks a BOOLEAN, so
            // "no" is a value it can be given - dropping it would turn a
            // deliberate no into an unanswered field.
            expect(toPromotionRunFieldValues({approved: false}))
                .toEqual([{name: 'approved', value: false}])
        })

        it('drops the fields the user left alone', () => {
            expect(toPromotionRunFieldValues({ticket: 'PROJ-42', note: '', other: undefined, missing: null}))
                .toEqual([{name: 'ticket', value: 'PROJ-42'}])
        })

        it('sends nothing at all when no field was filled', () => {
            // Not an empty list: the argument is optional and the level may
            // declare no field at all.
            expect(toPromotionRunFieldValues({})).toBeUndefined()
            expect(toPromotionRunFieldValues(undefined)).toBeUndefined()
        })
    })
})
