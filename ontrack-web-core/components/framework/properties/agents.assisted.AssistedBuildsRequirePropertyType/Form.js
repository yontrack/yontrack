import {Form} from "antd";
import LoadingContainer from "@components/common/LoadingContainer";
import {prefixedFormName} from "@components/form/formUtils";
import SelectValidationStamp from "@components/validationStamps/SelectValidationStamp";
import {usePromotionLevelBranch} from "@components/promotionLevels/UsePromotionLevelBranch";
import LicenceNotice from "./LicenceNotice";

export default function PropertyForm({prefix, entity}) {

    const {branch, loading, error} = usePromotionLevelBranch({promotionLevelId: entity.entityId})

    return (
        <>
            <LicenceNotice/>
            <LoadingContainer loading={loading} error={error}>
                {
                    branch &&
                    <Form.Item
                        label="Validation stamps"
                        extra="If the build is assisted - its commits were written with coding agents - these validations must pass before it is promoted to this level. A build whose assisted change is unknown, or not computed yet, counts as assisted."
                        name={prefixedFormName(prefix, 'validationStamps')}
                    >
                        <SelectValidationStamp
                            id="assisted-builds-require-validation-stamps"
                            branch={branch}
                            multiple={true}
                            useName={true}
                        />
                    </Form.Item>
                }
            </LoadingContainer>
        </>
    )
}
