import {Form, Input} from "antd";
import SelectProject from "@components/projects/SelectProject";

export default function OnPromotionValidationDataType({prefix, project, qualifier, promotion}) {
    return (
        <>
            <Form.Item
                name={[prefix, "project"]}
                label="Project"
                extra="Project of the linked build."
                initialValue={project}
                rules={[{required: true, message: "The project of the linked build is required."}]}
            >
                <SelectProject id="on-promotion-project"/>
            </Form.Item>
            <Form.Item
                name={[prefix, "qualifier"]}
                label="Qualifier"
                extra="Qualifier of the build link. Leave empty for the default link."
                initialValue={qualifier ?? ''}
            >
                <Input data-testid="on-promotion-qualifier"/>
            </Form.Item>
            <Form.Item
                name={[prefix, "promotion"]}
                label="Promotion"
                extra="Promotion the linked build must reach, in its own branch."
                initialValue={promotion}
                rules={[{required: true, message: "The promotion of the linked build is required."}]}
            >
                <Input data-testid="on-promotion-promotion"/>
            </Form.Item>
        </>
    )
}
