import CHMLDisplay from "@components/framework/validation-data-type/general.validation.CHMLValidationDataType"
import CHMLForm from "@components/framework/validation-data-type-form/general.validation.CHMLValidationDataType"
import FindingsDisplay from "@components/framework/validation-data-type/findings.validation.FindingsValidationDataType"
import FindingsForm from "@components/framework/validation-data-type-form/findings.validation.FindingsValidationDataType"

// #1944 - the configuration of a security findings stamp is the one of CHML, warningPassesAutoPromotion
// included: a findings stamp shows and edits it with the very components of CHML.
describe('the security findings validation data type', () => {

    it('is displayed as CHML', () => {
        expect(FindingsDisplay).toBe(CHMLDisplay)
    })

    it('is edited as CHML', () => {
        expect(FindingsForm).toBe(CHMLForm)
    })
})
