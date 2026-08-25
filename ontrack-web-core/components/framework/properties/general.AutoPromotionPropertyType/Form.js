import {Form, Input} from "antd";
import {prefixedFormName} from "@components/form/formUtils";
import SelectValidationStamp from "@components/validationStamps/SelectValidationStamp";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";
import LoadingContainer from "@components/common/LoadingContainer";
import {usePromotionLevelBranch} from "@components/promotionLevels/UsePromotionLevelBranch";

export default function PropertyForm({prefix, entity}) {

    const {entityType, entityId} = entity
    if (entityType !== 'PROMOTION_LEVEL') throw new Error(`Expecting a promotion level, got ${entityType}`)

    const {branch, loading, error} = usePromotionLevelBranch({promotionLevelId: entityId})

    return (
        <>
            <LoadingContainer loading={loading} error={error}>
                {
                    branch &&
                    <Form.Item
                        label="Validation stamps"
                        extra="List of validation stamps which trigger this promotion"
                        name={prefixedFormName(prefix, 'validationStamps')}
                    >
                        <SelectValidationStamp
                            dataTestId="auto-promotion-validation-stamps"
                            branch={branch}
                            multiple={true}
                            useName={false}
                        />
                    </Form.Item>
                }
                <Form.Item
                    label="Including"
                    extra="Regular expression to include validation stamps by name"
                    name={prefixedFormName(prefix, 'include')}
                >
                    <Input/>
                </Form.Item>
                <Form.Item
                    label="Excluding"
                    extra="Regular expression to exclude validation stamps by name"
                    name={prefixedFormName(prefix, 'exclude')}
                >
                    <Input/>
                </Form.Item>
                {
                    branch &&
                    <Form.Item
                        label="Promotion levels"
                        extra="List of promotion levels which trigger this promotion"
                        name={prefixedFormName(prefix, 'promotionLevels')}
                    >
                        <SelectPromotionLevel
                            dataTestId="auto-promotion-promotion-levels"
                            branch={branch}
                            multiple={true}
                            useName={false}
                        />
                    </Form.Item>
                }
            </LoadingContainer>
        </>
    )
}
