import {gql} from "graphql-request";
import {AutoComplete, Col, Form, Input, InputNumber, Radio, Row, Select, Space, Typography} from "antd";
import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import SelectLabel from "@components/labels/SelectLabel";
import {useQuery} from "@components/services/GraphQL";
import {
    DURATION,
    PER_WEEK,
    PERCENT,
    RUNG,
    readingDirectionSymbol,
    readingName,
    readingUnit,
} from "@components/extension/scorecard/scorecardModel";
import {
    DURATION_INPUT_UNITS,
    estateFormValues,
    estateInput,
    MARKER_DEFAULT,
    MARKER_ENVIRONMENT,
    MARKER_PROMOTION,
    rungTargetOptions,
} from "@components/extension/scorecard/estates/estateModel";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {FINDING_KINDS, kindName} from "@components/extension/findings/findingsModel";

const estateInputFields = `
    name: $name,
    description: $description,
    labels: $labels,
    marker: $marker,
    readings: $readings,
    security: $security,
`

const createQuery = gql`
    mutation CreateEstate(
        $name: String!,
        $description: String,
        $labels: [String!]!,
        $marker: EstateMarkerInput,
        $readings: [EstateReadingConfigInput!],
        $security: EstateSecurityInput,
    ) {
        createEstate(input: {${estateInputFields}}) {
            estate {
                id
            }
            errors {
                message
            }
        }
    }
`

const updateQuery = gql`
    mutation UpdateEstate(
        $id: Int!,
        $name: String!,
        $description: String,
        $labels: [String!]!,
        $marker: EstateMarkerInput,
        $readings: [EstateReadingConfigInput!],
        $security: EstateSecurityInput,
    ) {
        updateEstate(input: {id: $id, ${estateInputFields}}) {
            estate {
                id
            }
            errors {
                message
            }
        }
    }
`

/**
 * Dialog for both the creation and the edition of an estate: started with `{estate}` for an
 * edition, with `{}` for a creation.
 */
export const useEstateDialog = ({onSuccess}) => {
    return useFormDialog({
        onSuccess,
        init: (form, {estate}) => {
            form.resetFields()
            form.setFieldsValue(estateFormValues(estate))
        },
        prepareValues: (values, {estate}) => {
            const input = estateInput(values)
            return estate ? {...input, id: Number(estate.id)} : input
        },
        query: (context) => context?.estate ? updateQuery : createQuery,
        userNode: (context) => context?.estate ? 'updateEstate' : 'createEstate',
    })
}

/**
 * Names to choose from, among the ones the API gives, while leaving any other name possible: a
 * promotion level of an estate is read on each branch which has one, and an environment may not
 * exist yet, or not be allowed by the licence.
 */
const useNameOptions = (query, dataFn) => {
    const {data} = useQuery(query, {dataFn, initialData: []})
    return (data ?? []).map(name => ({value: name}))
}

const filterOption = (input, option) => option.value.toLowerCase().includes(input.toLowerCase())

function PromotionLevelNameInput(props) {
    const options = useNameOptions(
        gql`
            query EstatePredefinedPromotionLevels {
                predefinedPromotionLevels {
                    name
                }
            }
        `,
        data => data.predefinedPromotionLevels.map(it => it.name),
    )
    return <AutoComplete {...props} options={options} filterOption={filterOption} placeholder="GOLD"/>
}

function EnvironmentNameInput(props) {
    const options = useNameOptions(
        gql`
            query EstateEnvironments {
                environments {
                    name
                }
            }
        `,
        data => data.environments.map(it => it.name),
    )
    return <AutoComplete {...props} options={options} filterOption={filterOption} placeholder="production"/>
}

/**
 * Target of one reading, in the unit of the reading, after the symbol of its direction: a duration
 * as an amount in minutes, hours or days, a frequency per week, a rate in percent.
 */
function ReadingTargetInput({name, readingKey}) {
    const unit = readingUnit(readingKey)
    const label = `Target of ${readingName(readingKey)}`
    const symbol = readingDirectionSymbol(readingKey)
    if (unit === RUNG) {
        return (
            <Form.Item name={[name, 'target']} noStyle>
                <Select
                    aria-label={label}
                    allowClear
                    placeholder="None"
                    options={rungTargetOptions(readingKey)}
                    optionRender={option =>
                        <Space orientation="vertical" size={0}>
                            <span>{option.label}</span>
                            <SecondaryText style={{fontSize: 12, whiteSpace: 'normal'}}>{option.data.description}</SecondaryText>
                        </Space>
                    }
                    popupMatchSelectWidth={360}
                    style={{width: '15em'}}
                />
            </Form.Item>
        )
    }
    if (unit === DURATION) {
        return (
            <Space.Compact>
                <Form.Item name={[name, 'target']} noStyle>
                    <InputNumber aria-label={label} prefix={symbol} min={0} placeholder="None" style={{width: '8em'}}/>
                </Form.Item>
                <Form.Item name={[name, 'targetUnit']} noStyle>
                    <Select
                        aria-label={`Unit of the target of ${readingName(readingKey)}`}
                        options={DURATION_INPUT_UNITS.map(({value, label}) => ({value, label}))}
                        style={{width: '7em'}}
                    />
                </Form.Item>
            </Space.Compact>
        )
    }
    return (
        <Form.Item name={[name, 'target']} noStyle>
            <InputNumber
                aria-label={label}
                prefix={symbol}
                suffix={unit === PER_WEEK ? '/ week' : unit === PERCENT ? '%' : undefined}
                min={0}
                max={unit === PERCENT ? 100 : undefined}
                placeholder="None"
                style={{width: '15em'}}
            />
        </Form.Item>
    )
}

function EstateReadingsItems({form}) {
    return (
        <Form.List name="readings">
            {(fields) => (
                <Space orientation="vertical" size={8} style={{width: '100%'}}>
                    <Row gutter={8}>
                        <Col span={7}><Typography.Text strong>Reading</Typography.Text></Col>
                        <Col span={6}><Typography.Text strong>Window</Typography.Text></Col>
                        <Col span={11}><Typography.Text strong>Target</Typography.Text></Col>
                    </Row>
                    {
                        fields.map(({key, name}) => {
                            const readingKey = form.getFieldValue(['readings', name, 'key'])
                            return (
                                <Row key={key} gutter={8} align="middle" data-testid={`estate-reading-${readingKey}`}>
                                    <Col span={7}>
                                        <Form.Item name={[name, 'key']} hidden noStyle>
                                            <Input/>
                                        </Form.Item>
                                        <Typography.Text title={readingKey}>{readingName(readingKey)}</Typography.Text>
                                    </Col>
                                    <Col span={6}>
                                        <Form.Item name={[name, 'windowDays']} noStyle>
                                            <InputNumber
                                                aria-label={`Window of ${readingName(readingKey)}`}
                                                min={1}
                                                precision={0}
                                                suffix="days"
                                                placeholder="Default"
                                                style={{width: '100%'}}
                                            />
                                        </Form.Item>
                                    </Col>
                                    <Col span={11}>
                                        <ReadingTargetInput name={name} readingKey={readingKey}/>
                                    </Col>
                                </Row>
                            )
                        })
                    }
                </Space>
            )}
        </Form.List>
    )
}

/**
 * Creation and edition of an estate: its name, the labels selecting its projects, the marker its
 * delivery readings are read up to, the window and target of each reading, and what it expects of
 * the security scans of its projects.
 */
export default function EstateDialog({dialog}) {

    const markerKind = Form.useWatch('markerKind', dialog.form)

    return (
        <FormDialog
            dialog={dialog}
            id="estate-dialog"
            width={760}
            header={
                <Typography.Title level={4} style={{marginTop: 0}}>
                    {dialog.context?.estate ? `Edit the ${dialog.context.estate.name} estate` : 'New estate'}
                </Typography.Title>
            }
        >
            <Form.Item
                name="name"
                label="Name"
                rules={[
                    {required: true, whitespace: true, message: "The name is required."},
                    {max: 100, message: "The name is limited to 100 characters."},
                ]}
            >
                <Input placeholder="Unique name of the estate"/>
            </Form.Item>
            <Form.Item
                name="description"
                label="Description"
                rules={[{max: 500, message: "The description is limited to 500 characters."}]}
            >
                <Input placeholder="Optional description of the estate"/>
            </Form.Item>
            <Form.Item
                name="labels"
                label="Labels"
                extra="The estate selects the projects carrying all of these labels."
                rules={[{required: true, type: 'array', min: 1, message: "One label at least is required."}]}
            >
                <SelectLabel multiple placeholder="Labels selecting the projects"/>
            </Form.Item>
            <Form.Item
                name="markerKind"
                label="Marker"
                extra={
                    markerKind === MARKER_PROMOTION ?
                        "The delivery readings are read up to this promotion level, on each branch which has one." :
                        markerKind === MARKER_ENVIRONMENT ?
                            "The delivery readings are read up to the deployments in the slots of this environment." :
                            "The highest-ordered environment where the project owns a slot, else the last promotion level of each branch."
                }
            >
                <Radio.Group
                    optionType="button"
                    options={[
                        {value: MARKER_DEFAULT, label: 'Default'},
                        {value: MARKER_PROMOTION, label: 'Promotion level'},
                        {value: MARKER_ENVIRONMENT, label: 'Environment'},
                    ]}
                />
            </Form.Item>
            {
                markerKind === MARKER_PROMOTION &&
                <Form.Item
                    name="levelName"
                    label="Level name"
                    rules={[{required: true, whitespace: true, message: "The name of the promotion level is required."}]}
                >
                    <PromotionLevelNameInput/>
                </Form.Item>
            }
            {
                markerKind === MARKER_ENVIRONMENT &&
                <Row gutter={8}>
                    <Col span={12}>
                        <Form.Item
                            name="environment"
                            label="Environment name"
                            rules={[{required: true, whitespace: true, message: "The name of the environment is required."}]}
                        >
                            <EnvironmentNameInput/>
                        </Form.Item>
                    </Col>
                    <Col span={12}>
                        <Form.Item
                            name="qualifier"
                            label="Qualifier"
                            extra="Empty for the default slots only."
                        >
                            <Input placeholder="Default"/>
                        </Form.Item>
                    </Col>
                </Row>
            }
            <Form.Item
                label="Readings"
                extra="A window overrides the one of the settings. A reading with no target is shown, not judged."
            >
                <EstateReadingsItems form={dialog.form}/>
            </Form.Item>
            <Form.Item
                name="expectedKinds"
                label="Expected scans"
                extra="Kinds of security scan every project must have run, each within the freshness, to be covered. None: any recent scan covers a project."
            >
                <Select
                    mode="multiple"
                    allowClear
                    placeholder="Any kind of scan"
                    options={FINDING_KINDS.map(kind => ({value: kind, label: kindName(kind)}))}
                />
            </Form.Item>
            <Row gutter={8}>
                <Col span={8}>
                    <Form.Item
                        name="freshnessDays"
                        label="Scan freshness"
                        extra="Empty for the one of the settings."
                    >
                        <InputNumber min={1} precision={0} suffix="days" placeholder="Default" style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={8}>
                    <Form.Item
                        name="criticalTargetDays"
                        label="CRITICAL fixed within"
                        extra="Empty for no target."
                    >
                        <InputNumber min={0} precision={0} suffix="days" placeholder="None" style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
                <Col span={8}>
                    <Form.Item
                        name="highTargetDays"
                        label="HIGH fixed within"
                        extra="Empty for no target."
                    >
                        <InputNumber min={0} precision={0} suffix="days" placeholder="None" style={{width: '100%'}}/>
                    </Form.Item>
                </Col>
            </Row>
        </FormDialog>
    )
}
