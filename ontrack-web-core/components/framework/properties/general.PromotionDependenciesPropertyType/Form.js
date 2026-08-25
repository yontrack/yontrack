import LoadingContainer from "@components/common/LoadingContainer";
import {Form} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";
import {usePromotionLevelBranch} from "@components/promotionLevels/UsePromotionLevelBranch";

export default function PropertyForm({prefix, entity}) {

    const {branch, loading} = usePromotionLevelBranch({promotionLevelId: entity.entityId})

    return (
        <>
            <LoadingContainer loading={loading}>
                {
                    branch &&
                    <Form.Item
                        label="Dependencies"
                        extra="Promotions this one depends on."
                        name={prefixedFormName(prefix, 'dependencies')}
                    >
                        <SelectPromotionLevel branch={branch} multiple={true} useName={true}/>
                    </Form.Item>
                }
            </LoadingContainer>
        </>
    )
}