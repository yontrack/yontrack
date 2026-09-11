"use client"

/**
 * A promotion level's declared fields, as a form.
 *
 * A promotion level can declare typed fields — `TEXT`, `NUMBER`, `BOOLEAN`,
 * `CHOICE`, `LINK` — and some of them can be required. The server enforces both:
 * `StructureServiceImpl.validatePromotionRunFieldValues` refuses a promotion run
 * missing a required field, and refuses a value whose JSON type does not match
 * the declared one.
 *
 * **This module is shared by both UIs on purpose**, and it is the *only* thing
 * the desktop promote dialog and the mobile promote sheet share. The two layouts
 * stay apart — a modal with a labelled column on one side, a bottom sheet sized
 * for a thumb on the other — but a field type added on the server must not
 * render on the desktop and silently fail to render on a phone, which is exactly
 * what two copies of a `switch` produce. A required field a phone cannot fill
 * makes that promotion level impossible to use from a phone.
 *
 * Everything here is pure or returns an element; nothing fetches, and nothing
 * knows which UI is asking. The one concession to the caller is `size`, because
 * a control sized for a pointer is not sized for a thumb.
 *
 * See `doc/dev-guide/ui/mobile-ui.md`.
 */

import {Checkbox, Input, InputNumber, Select} from "antd"
import {gql} from "graphql-request"

/**
 * What both UIs ask for about a level's fields.
 *
 * A fragment rather than two hand-written selection sets: a field the mapping
 * below reads has to be a field the query asked for, and the two drifting apart
 * is a `undefined` in a label rather than an error.
 */
export const gqlPromotionLevelFieldSet = gql`
    fragment PromotionLevelFieldSet on PromotionLevelField {
        name
        displayName
        description
        type
        required
        options
        position
    }
`

/**
 * The fields of a level, in the order they are meant to be shown.
 *
 * @param {Array} [fields] What the server answered with.
 * @returns {Array} A new array - the caller's own is left alone, because it
 *   usually belongs to a query result that other things read too.
 */
export function orderedPromotionLevelFields(fields) {
    return [...(fields ?? [])].sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
}

/**
 * Which prop an antd `Form.Item` binds this field's value through.
 *
 * A checkbox carries its value in `checked`; everything else in `value`.
 *
 * @param {Object} field
 * @returns {'checked'|'value'}
 */
export function promotionLevelFieldValuePropName(field) {
    return field?.type === 'BOOLEAN' ? 'checked' : 'value'
}

/**
 * The validation rules for a field.
 *
 * Client-side, so a user is told which field is missing before the round trip;
 * the server refuses it either way, and the message it answers with names the
 * technical key rather than the label the user was shown.
 *
 * @param {Object} field
 * @returns {Array} antd rules - empty for an optional field.
 */
export function promotionLevelFieldRules(field) {
    return field?.required
        ? [{required: true, message: `${field.displayName} is required.`}]
        : []
}

/**
 * The input control a field type gets.
 *
 * @param {Object} field The declared field.
 * @param {Object} [options]
 * @param {'small'|'middle'|'large'} [options.size] How big the control is. The
 *   mobile UI asks for `large`, which is what puts these over the 44px a thumb
 *   wants; the desktop leaves it alone.
 * @returns {React.ReactElement} A controlled input, to be handed to a
 *   `Form.Item` - it takes its `value`/`onChange` from the form, not from here.
 */
export function promotionLevelFieldInput(field, {size} = {}) {
    switch (field?.type) {
        case 'NUMBER':
            return <InputNumber size={size} style={{width: '100%'}}/>
        case 'BOOLEAN':
            /*
             * No `size`: antd's `Checkbox` has no such prop and passes the rest
             * through to the <input>, where `size` is a *numeric* HTML attribute
             * - React rejects `"large"` on every render. The tap target comes
             * from the label beside it instead.
             */
            return <Checkbox/>
        case 'CHOICE':
            return (
                <Select
                    size={size}
                    options={(field.options ?? []).map(option => ({label: option, value: option}))}
                    /*
                     * Inside the dialog or the sheet rather than on <body>: a
                     * popup rendered at the document root sits behind a modal
                     * and outside a drawer.
                     */
                    getPopupContainer={trigger => trigger.parentElement}
                />
            )
        case 'LINK':
            // Told apart from TEXT by the placeholder alone, which is the whole
            // difference the server makes of it too: both are JSON strings.
            return <Input size={size} placeholder="https://..."/>
        case 'TEXT':
        default:
            /*
             * A type this build has never heard of still gets a box. The server
             * may declare a type a deployed UI does not know - the enum is
             * extended server-side - and a field rendered as nothing is a
             * required field nobody can fill. A string is what an unknown JSON
             * scalar most likely is, and the server type-checks it anyway.
             */
            return <Input size={size}/>
    }
}

/**
 * The form's field values, as the mutation's `fieldValues` argument.
 *
 * @param {Object} [values] What the form holds, keyed by field name.
 * @returns {Array|undefined} `PromotionRunFieldValueInput` entries, or
 *   `undefined` when nothing was filled - the argument is optional and most
 *   levels declare no field at all.
 */
export function toPromotionRunFieldValues(values) {
    const fieldValues = Object.entries(values ?? {})
        /*
         * `false` is kept: `value` is a JSON scalar and the server type-checks a
         * BOOLEAN, so "no" is an answer rather than an absence. What is dropped
         * is the three ways a form says "untouched".
         */
        .filter(([, value]) => value !== undefined && value !== null && value !== '')
        .map(([name, value]) => ({name, value}))
    return fieldValues.length > 0 ? fieldValues : undefined
}
