import Well from "@components/common/Well";
import {Button, Flex, Form, Input, InputNumber, Space} from "antd";
import {FaEraser, FaLink, FaSearch} from "react-icons/fa";
import SelectPromotionLevelForProject from "@components/promotionLevels/SelectPromotionLevelForProject";
import {useContext, useEffect, useState} from "react";
import {UserContext} from "@components/providers/UserProvider";
import SelectEnvironmentName from "@components/extension/environments/SelectEnvironmentName";
import SelectPropertyType from "@components/core/model/properties/SelectPropertyType";
import {useRouter} from "next/router";
import {projectBuildSearchUri} from "@components/common/Links";

export default function ProjectBuildSearchForm({project, loading, onSubmit}) {

    const router = useRouter()
    const user = useContext(UserContext)

    const [form] = Form.useForm()

    useEffect(() => {
        const values = router.query
        if (values) {
            form.setFieldsValue(values)
        }
    }, [form, router.query])

    const resetForm = () => {
        form.resetFields()
    }

    const [advancedDisplayed, setAdvancedDisplayed] = useState(false)

    const toggleAdvanced = () => {
        setAdvancedDisplayed(value => !value)
    }

    const extractFormValues = () => {
        const values = form.getFieldsValue()
        return Object.entries(values)
            .reduce((acc, [key, value]) => {
                if (value && form.isFieldTouched(key)) {
                    acc[key] = value
                }
                return acc
            }, {})
    }

    const setPermalink = async () => {
        await router.replace({
            pathname: projectBuildSearchUri(project),
            query: extractFormValues(),
        }, undefined, {shallow: true})
    }

    return (
        <>
            <Well>
                <Form
                    form={form}
                    layout="inline"
                    onFinish={onSubmit}
                    disabled={loading}
                >
                    <Space orientation="vertical" className="ot-line">
                        {/* Common fields */}
                        <Space>
                            {/* Branch name */}
                            <Form.Item
                                name="branchName"
                                label="Branch"
                            >
                                <Input
                                    style={{width: '12em'}}
                                    placeholder="Branch regex"
                                />
                            </Form.Item>
                            {/* Build name */}
                            <Form.Item
                                name="buildName"
                                label="Build"
                            >
                                <Input
                                    style={{width: '12em'}}
                                    placeholder="Build name"
                                />
                            </Form.Item>
                            {/* Promotion name */}
                            <Form.Item
                                name="promotionName"
                                label="Promotion"
                            >
                                <SelectPromotionLevelForProject
                                    project={project}
                                />
                            </Form.Item>
                            {/* Environment */}
                            {
                                user.authorizations.environment?.view &&
                                <Form.Item
                                    name="environmentName"
                                    label="Environment"
                                >
                                    <SelectEnvironmentName/>
                                </Form.Item>
                            }
                            {/* Validation name */}
                            <Form.Item
                                name="validationStampName"
                                label="Validation"
                            >
                                <Input style={{width: '12em'}}/>
                            </Form.Item>
                        </Space>
                        {/* Advanced section */}
                        <Space style={{
                            display: advancedDisplayed ? undefined : "none",
                        }}>
                            <Form.Item
                                name="maximumCount"
                                label="Count"
                                initialValue={10}
                            >
                                <InputNumber min={1} step={1}/>
                            </Form.Item>
                            <Form.Item
                                name="property"
                                label="Property"
                            >
                                <SelectPropertyType projectEntityType="BUILD"/>
                            </Form.Item>
                            <Form.Item
                                name="propertyValue"
                                label="Property value"
                            >
                                <Input style={{width: '12em'}}/>
                            </Form.Item>
                        </Space>
                        {/* Buttons */}
                        <Flex justify="flex-end" align="center">
                            {/* Search button */}
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={loading}
                                icon={<FaSearch/>}
                            >
                                <Space>
                                    Search
                                </Space>
                            </Button>
                            {/* Advanced filters */}
                            <Button
                                type="link"
                                onClick={toggleAdvanced}
                            >
                                Advanced
                            </Button>
                            {/*Reset */}
                            <Button
                                type="default"
                                onClick={resetForm}
                            >
                                <Space>
                                    <FaEraser/>
                                    Reset
                                </Space>
                            </Button>
                            {/* Permalink */}
                            <Button
                                type="link"
                                icon={<FaLink/>}
                                title="Sets a permalink in the address bar"
                                onClick={setPermalink}
                            />
                        </Flex>
                    </Space>
                </Form>
            </Well>
        </>
    )
}