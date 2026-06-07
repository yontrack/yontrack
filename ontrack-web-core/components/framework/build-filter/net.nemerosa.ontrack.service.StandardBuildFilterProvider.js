import {Col, DatePicker, Form, Input, Row, Select, Tabs, Typography} from "antd";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";
import SelectValidationStamp from "@components/validationStamps/SelectValidationStamp";
import SelectValidationRunStatus from "@components/validationRuns/SelectValidationRunStatus";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";

export default function StandardBuildFilterProvider({branch, buildFilterForm}) {

    const client = useGraphQLClient()

    const [properties, setProperties] = useState([])
    useEffect(() => {
        if (client) {
            client.request(
                gql`
                    query GetBranchProperties {
                        properties(projectEntityType: BUILD) {
                            value: typeName
                            label: name
                        }
                    }
                `
            ).then(data => {
                setProperties(data.properties)
            })
        }
    }, [client]);

    const tabs = [
        {
            key: 'promotion',
            label: "Promotion",
            children: <>
                {/* With promotion level */}
                <Form.Item
                    name={['data', 'withPromotionLevel']}
                    label="With promotion level"
                >
                    <SelectPromotionLevel
                        branch={branch}
                        useName={true}
                        allowClear={true}
                    />
                </Form.Item>
                {/* Since promotion level */}
                <Form.Item
                    name={['data', 'sincePromotionLevel']}
                    label="Since promotion level"
                >
                    <SelectPromotionLevel
                        branch={branch}
                        useName={true}
                        allowClear={true}
                    />
                </Form.Item>
            </>
        },
        {
            key: 'validation',
            label: "Validation",
            children: <>
                <Row gutter={16}>
                    <Col span={12}>
                        {/* With validation stamp */}
                        <Form.Item
                            name={['data', 'withValidationStamp']}
                            label="With validation stamp"
                        >
                            <SelectValidationStamp
                                branch={branch}
                                useName={true}
                                allowClear={true}
                            />
                        </Form.Item>
                    </Col>
                    <Col span={12}>
                        {/* With validation stamp with status */}
                        <Form.Item
                            name={['data', 'withValidationStampStatus']}
                            label="... with status"
                        >
                            <SelectValidationRunStatus all={true}/>
                        </Form.Item>
                    </Col>
                </Row>
                <Row gutter={16}>
                    <Col span={12}>
                        {/* Since validation stamp */}
                        <Form.Item
                            name={['data', 'sinceValidationStamp']}
                            label="Since validation stamp"
                        >
                            <SelectValidationStamp
                                branch={branch}
                                useName={true}
                                allowClear={true}
                            />
                        </Form.Item>
                    </Col>
                    <Col span={12}>
                        {/* Since validation stamp with status */}
                        <Form.Item
                            name={['data', 'sinceValidationStampStatus']}
                            label="... with status"
                        >
                            <SelectValidationRunStatus all={true}/>
                        </Form.Item>
                    </Col>
                </Row>
            </>
        },
        {
            key: 'property',
            label: "Property",
            children: <>
                <Row gutter={16}>
                    {/* With property */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'withProperty']}
                            label="With property"
                        >
                            <Select options={properties} allowClear={true}/>
                        </Form.Item>
                    </Col>
                    {/* With property with value */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'withPropertyValue']}
                            label="... with value"
                        >
                            <Input/>
                        </Form.Item>
                    </Col>
                </Row>
                <Row gutter={16}>
                    {/* Since property */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'sinceProperty']}
                            label="Since property"
                        >
                            <Select options={properties} allowClear={true}/>
                        </Form.Item>
                    </Col>
                    {/* Since property with value */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'sincePropertyValue']}
                            label="... with value"
                        >
                            <Input/>
                        </Form.Item>
                    </Col>
                </Row>
            </>
        },
        {
            key: 'link',
            label: "Links",
            children: <>
                <Row gutter={16}>
                    {/* Linked from */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'linkedFrom']}
                            label="Linked from"
                            extra="The build must be linked FROM the builds selected by the pattern. Syntax: PRJ:BLD where PRJ is a project name and BLD a build expression - with * as placeholder"
                        >
                            <Input/>
                        </Form.Item>
                    </Col>
                    {/* Linked from promotion level */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'linkedFromPromotion']}
                            label="... with promotion"
                            extra="The build must be linked FROM a build having this promotion"
                        >
                            <SelectValidationStamp
                                branch={branch}
                                useName={true}
                                allowClear={true}
                            />
                        </Form.Item>
                    </Col>
                </Row>
                <Row gutter={16}>
                    {/* Linked to */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'linkedTo']}
                            label="Linked to"
                            extra="The build must be linked TO the builds selected by the pattern. Syntax: PRJ:BLD where PRJ is a project name and BLD a build expression - with * as placeholder"
                        >
                            <Input/>
                        </Form.Item>
                    </Col>
                    {/* Linked to promotion level */}
                    <Col span={12}>
                        <Form.Item
                            name={['data', 'linkedToPromotion']}
                            label="... with promotion"
                            extra="The build must be linked TO a build having this promotion"
                        >
                            <SelectValidationStamp
                                branch={branch}
                                useName={true}
                            />
                        </Form.Item>
                    </Col>
                </Row>
            </>
        },
        {
            key: 'time',
            label: "Time",
            children: <>
                <Row gutter={16}>
                    <Col span={12}>
                        {/* Build after */}
                        <Form.Item
                            name={['data', 'afterDate']}
                            label="Build after"
                            extra="Build created after or on this date"
                        >
                            <DatePicker/>
                        </Form.Item>
                    </Col>
                    <Col span={12}>
                        {/* Build before */}
                        <Form.Item
                            name={['data', 'beforeDate']}
                            label="Build before"
                            extra="Build created before or on this date"
                        >
                            <DatePicker/>
                        </Form.Item>
                    </Col>
                </Row>
            </>
        },
    ]

    return (
        <>
            {/* Name of the filter type (static) */}
            <Typography.Title level={3}>{buildFilterForm.typeName}</Typography.Title>
            {/* Name */}
            <Form.Item
                name={['data', 'name']}
                label="Name of the filter"
                extra="Optional name to save the filter"
            >
                <Input/>
            </Form.Item>

            <Tabs
                items={tabs}
            />
        </>
    )
}