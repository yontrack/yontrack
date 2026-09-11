"use client"

/**
 * Promoting a build, from a phone.
 *
 * **A bottom sheet, not the desktop dialog.** `BuildPromoteDialog` is a modal
 * with a labelled column beside every control, and it still reads the server
 * through the deprecated `useGraphQLClient`. Both halves of the boundary in
 * `doc/dev-guide/ui/mobile-ui.md` apply: the layout is the mobile UI's own, and
 * the read and the write go through `@components/services/GraphQL`.
 *
 * **What it does share is the field mapping**, and only that - see
 * `components/promotionLevels/promotionLevelFields.js`. A promotion level can
 * declare typed fields and some of them are required, so a field type this
 * screen cannot render is a promotion level a phone cannot use at all. Two
 * copies of that `switch` would drift the first time a type is added, and the
 * mobile half would fail silently: the field would simply not be there. The
 * layout stays separate; the type mapping does not have to be.
 *
 * **The sheet's shape.** The form is one column of full-width controls - a
 * phone has no room for a label beside a control - and the two buttons sit at
 * the bottom, inside the form, so the primary one is a real submit button.
 *
 * @param {Object} build The build being promoted - its `id`, and its `branch`,
 *   whose promotion levels are the choice on offer.
 * @param {boolean} open Whether the sheet is up.
 * @param {function} onClose Close it, whatever the reason.
 * @param {function} onPromoted Called once the server has created the run, so
 *   the screen behind can refetch. It refetches rather than patching its copy:
 *   the run's id, its signature and its position among the level's runs are all
 *   the server's answer, not something the phone can invent.
 */

import {useState} from "react"
import {gql} from "graphql-request"
import {Alert, Button, DatePicker, Drawer, Form, Input, Select, Space, Typography} from "antd"
import dayjs from "dayjs"
import {callGraphQL, useQuery} from "@components/services/GraphQL"
import {useMessageApi} from "@components/providers/MessageProvider"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import {
    gqlPromotionLevelFieldSet,
    orderedPromotionLevelFields,
    promotionLevelFieldInput,
    promotionLevelFieldRules,
    promotionLevelFieldValuePropName,
    toPromotionRunFieldValues,
} from "@components/promotionLevels/promotionLevelFields"

/** The medal beside a level's name, at a size that reads on a phone. */
const MEDAL_SIZE = 20

export default function MobilePromoteSheet({build, open, onClose, onPromoted}) {
    return (
        <Drawer
            // A sheet rising from the bottom: the phone pattern, and the half of
            // the screen a thumb actually reaches. `auto` because the form is
            // three controls for most levels and a dozen for a few.
            placement="bottom"
            /*
             * As tall as it needs to be, and never taller than the phone. A
             * level declaring five fields makes a form longer than an 812px
             * screen, and an `auto` height with no cap puts the Promote button
             * below the bottom of the window with nothing to scroll it into
             * view: the drawer grows, the page behind it does not.
             *
             * **Both** halves are capped, and that is not belt and braces: the
             * wrapper's own overflow is `visible`, so a capped wrapper around an
             * uncapped content leaves the content hanging out of the bottom of
             * it, exactly as if there were no cap at all. Capping the content
             * makes the drawer's body - a flex child with `overflow: auto` -
             * the scroller, which keeps the title in place while the form
             * scrolls under it.
             */
            height="auto"
            styles={{wrapper: {maxHeight: '85vh'}, content: {maxHeight: '85vh'}}}
            title="Promote build"
            open={open}
            onClose={onClose}
            // Every opening starts from a blank form. The alternative is a sheet
            // remembering the level, the description and the fields of a
            // promotion the user decided against.
            destroyOnClose
        >
            <MobilePromoteForm build={build} onClose={onClose} onPromoted={onPromoted}/>
        </Drawer>
    )
}

/**
 * The form itself, mounted only while the sheet is open.
 *
 * Separate from the sheet so that the query below - and every piece of form
 * state - belongs to one opening of it. A build screen mounts this for every
 * build a user looks at, and a phone should not pay for a sheet nobody opened.
 */
function MobilePromoteForm({build, onClose, onPromoted}) {

    const [form] = Form.useForm()
    const messageApi = useMessageApi()

    const [running, setRunning] = useState(false)
    const [error, setError] = useState(null)

    /*
     * The time is collapsed until someone asks for it: a person promoting from
     * their phone is promoting now. Genuine UI state - what the user asked to
     * see - rather than anything derived.
     */
    const [timing, setTiming] = useState(false)

    const branchId = build?.branch?.id

    const query = useQuery(
        gql`
            query MobilePromotionLevels($branchId: Int!) {
                branches(id: $branchId) {
                    promotionLevels {
                        id
                        name
                        image
                        # What makes a level usable from a phone or not.
                        fields {
                            ...PromotionLevelFieldSet
                        }
                    }
                }
            }
            ${gqlPromotionLevelFieldSet}
        `,
        {
            variables: {branchId: Number(branchId)},
            deps: [branchId],
            condition: !!branchId,
            dataFn: data => data.branches?.[0]?.promotionLevels ?? [],
            initialData: [],
        }
    )

    const promotionLevels = query.data ?? []

    // Derived from the form, not copied into state: the fields on show are the
    // fields of the level currently picked, and nothing else decides them.
    const selected = Form.useWatch('promotionLevel', form)
    const fields = orderedPromotionLevelFields(
        promotionLevels.find(promotionLevel => promotionLevel.name === selected)?.fields
    )

    const onSubmit = async (values) => {
        setRunning(true)
        setError(null)
        try {
            const data = await callGraphQL({
                query: gql`
                    mutation MobilePromoteBuild(
                        $buildId: Int!,
                        $promotion: String!,
                        $description: String,
                        $dateTime: LocalDateTime,
                        $fieldValues: [PromotionRunFieldValueInput!]
                    ) {
                        createPromotionRunById(input: {
                            buildId: $buildId,
                            promotion: $promotion,
                            description: $description,
                            dateTime: $dateTime,
                            fieldValues: $fieldValues,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    buildId: Number(build.id),
                    promotion: values.promotionLevel,
                    description: values.description || undefined,
                    /*
                     * Omitted while the row stays collapsed, rather than sent as
                     * the moment the sheet was opened: the server stamps the run
                     * with the time it actually receives it, and a sheet can sit
                     * open on a phone while its owner is interrupted.
                     */
                    dateTime: timing ? values.dateTime : undefined,
                    fieldValues: toPromotionRunFieldValues(values.fieldValues),
                },
            })
            const errors = data?.createPromotionRunById?.errors
            if (errors && errors.length > 0) {
                // Inline rather than as a toast: it is usually about a field on
                // this form, and the user has to be able to read it and fix it.
                setError(errors[0].message)
            } else {
                messageApi?.success(`Build promoted to ${values.promotionLevel}.`)
                onPromoted?.()
                onClose?.()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setRunning(false)
        }
    }

    return (
        <Form
            form={form}
            // One column of full-width controls: at 375px a label beside a
            // control leaves neither enough room.
            layout="vertical"
            // Every control over the 44px a thumb wants, in one place.
            size="large"
            onFinish={onSubmit}
        >
            {
                query.error &&
                <Alert
                    type="error"
                    showIcon
                    message="Could not load the promotion levels."
                    description={query.error}
                    style={{marginBottom: 16}}
                />
            }
            <Form.Item
                name="promotionLevel"
                label="Promotion level"
                rules={[{required: true, message: 'Promotion level is required.'}]}
            >
                <Select
                    placeholder="Choose a promotion level"
                    loading={query.loading}
                    data-testid="mobile-promote-level"
                    getPopupContainer={trigger => trigger.parentElement}
                    /*
                     * The abandoned level's answers go with it. Two levels can
                     * both declare a `ticket`, and carrying a value across would
                     * promote with an answer to a question the user was never
                     * asked.
                     */
                    onChange={() => form.setFieldValue('fieldValues', undefined)}
                    options={
                        promotionLevels.map(promotionLevel => ({
                            // The name, which is what `createPromotionRunById`
                            // takes - an id would find nothing.
                            value: promotionLevel.name,
                            // Explicit, because the label below is an element:
                            // antd only derives a title from a string label.
                            title: promotionLevel.name,
                            label: (
                                <span className="ot-mobile-inline">
                                    <PromotionLevelImage promotionLevel={promotionLevel} size={MEDAL_SIZE}/>
                                    <span>{promotionLevel.name}</span>
                                </span>
                            ),
                        }))
                    }
                />
            </Form.Item>

            {
                /*
                 * The level's own fields, in their declared order. Rendered
                 * through the shared mapping so that a type added on the desktop
                 * appears here too - see `promotionLevelFields`.
                 */
                fields.map(field => (
                    <Form.Item
                        key={field.name}
                        name={['fieldValues', field.name]}
                        label={field.displayName}
                        extra={field.description}
                        rules={promotionLevelFieldRules(field)}
                        valuePropName={promotionLevelFieldValuePropName(field)}
                    >
                        {promotionLevelFieldInput(field, {size: 'large'})}
                    </Form.Item>
                ))
            }

            <Form.Item
                name="description"
                label="Description"
            >
                <Input.TextArea rows={2} data-testid="mobile-promote-description"/>
            </Form.Item>

            {
                timing ?
                    <Form.Item
                        name="dateTime"
                        label="Date/time"
                        rules={[{required: true, message: 'Promotion time is required.'}]}
                    >
                        <DatePicker
                            showTime
                            style={{width: '100%'}}
                            data-testid="mobile-promote-time"
                            getPopupContainer={trigger => trigger.parentElement}
                        />
                    </Form.Item> :
                    <Space direction="vertical" size={0} style={{marginBottom: 16}}>
                        <Typography.Text type="secondary" className="ot-mobile-caption">
                            Promoted now
                        </Typography.Text>
                        <Button
                            type="link"
                            size="small"
                            style={{paddingLeft: 0}}
                            data-testid="mobile-promote-time-toggle"
                            onClick={() => {
                                // Opened on the current time, which is the only
                                // sensible place for a correction to start.
                                form.setFieldValue('dateTime', dayjs())
                                setTiming(true)
                            }}
                        >
                            Promoted earlier?
                        </Button>
                    </Space>
            }

            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    message={error}
                    data-testid="mobile-promote-error"
                    style={{marginBottom: 16}}
                />
            }

            <Space direction="vertical" size="small" style={{width: '100%'}}>
                <Button
                    block
                    type="primary"
                    htmlType="submit"
                    loading={running}
                    data-testid="mobile-promote-submit"
                >
                    Promote
                </Button>
                <Button block onClick={onClose} data-testid="mobile-promote-cancel">
                    Cancel
                </Button>
            </Space>
        </Form>
    )
}
