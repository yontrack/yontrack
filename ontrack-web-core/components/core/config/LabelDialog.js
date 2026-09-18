import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {ColorPicker, Form, Input} from "antd";
import {gql} from "graphql-request";
import {brand} from "@components/common/brand/Colors";

/**
 * Same rules as `LabelForm` on the server side.
 */
export const labelNameRegex = /^[A-Za-z0-9.\-_]+$/
export const labelColorRegex = /^#[a-fA-F0-9]{6}$/

/**
 * The colour a new label gets when the picker is never opened.
 *
 * It is the brand gray rather than antd's own blue: a quiet neutral reads as
 * "unclassified" and lets the labels somebody did colour on purpose stand out.
 * Taken from the palette rather than written out again, so it follows the brand.
 */
const defaultColor = brand.colors.gray

/**
 * The Ant Design `ColorPicker` puts a `Color` object in the form, not a string,
 * and only when the user actually picks a colour - the initial value stays
 * whatever was set. Both cases end up as `#RRGGBB` here.
 */
export const colorToHex = (color) => {
    if (!color) return defaultColor
    return typeof color === 'string' ? color : color.toHexString()
}

const createQuery = gql`
    mutation CreateLabel(
        $category: String,
        $name: String!,
        $description: String,
        $color: String!,
    ) {
        createLabel(input: {
            category: $category,
            name: $name,
            description: $description,
            color: $color,
        }) {
            errors {
                message
            }
        }
    }
`

const updateQuery = gql`
    mutation UpdateLabel(
        $id: Int!,
        $category: String,
        $name: String!,
        $description: String,
        $color: String!,
    ) {
        updateLabel(input: {
            id: $id,
            category: $category,
            name: $name,
            description: $description,
            color: $color,
        }) {
            errors {
                message
            }
        }
    }
`

/**
 * Dialog used for both the creation and the edition of a label. It is started
 * with `{label}` for an edition and with `{}` for a creation.
 */
export const useLabelDialog = ({onSuccess}) => {
    return useFormDialog({
        onSuccess,
        init: (form, {label}) => {
            form.setFieldsValue({
                category: label?.category ?? '',
                name: label?.name ?? '',
                description: label?.description ?? '',
                color: label?.color ?? defaultColor,
            })
        },
        prepareValues: (values, {label}) => {
            const prepared = {
                category: values.category ? values.category : null,
                name: values.name,
                description: values.description ? values.description : null,
                color: colorToHex(values.color),
            }
            return label ? {...prepared, id: Number(label.id)} : prepared
        },
        query: (context) => context?.label ? updateQuery : createQuery,
        userNode: (context) => context?.label ? 'updateLabel' : 'createLabel',
    })
}

export default function LabelDialog({dialog}) {
    return (
        <>
            <FormDialog dialog={dialog} id="label-dialog">
                <Form.Item
                    name="category"
                    label="Category"
                    rules={[
                        {
                            pattern: labelNameRegex,
                            message: "The category must comply with format [A-Za-z0-9.-_]",
                        },
                    ]}
                >
                    <Input placeholder="Optional category of the label"/>
                </Form.Item>
                <Form.Item
                    name="name"
                    label="Name"
                    rules={[
                        {required: true, message: "Name is required"},
                        {
                            pattern: labelNameRegex,
                            message: "The name must comply with format [A-Za-z0-9.-_]",
                        },
                    ]}
                >
                    <Input placeholder="Name of the label"/>
                </Form.Item>
                <Form.Item
                    name="description"
                    label="Description"
                >
                    <Input placeholder="Optional description of the label"/>
                </Form.Item>
                <Form.Item
                    name="color"
                    label="Color"
                    rules={[
                        {
                            validator: (_, value) =>
                                labelColorRegex.test(colorToHex(value)) ?
                                    Promise.resolve() :
                                    Promise.reject(new Error("The color must comply with format #RRGGBB")),
                        },
                    ]}
                >
                    <ColorPicker
                        format="hex"
                        disabledAlpha
                        showText
                    />
                </Form.Item>
            </FormDialog>
        </>
    )
}
