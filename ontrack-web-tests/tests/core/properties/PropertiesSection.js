import {expect} from "@playwright/test";
import {PropertyDialog} from "./PropertyDialog";

/**
 * The "Properties" section displayed in the information drawer of an entity.
 */
export class PropertiesSection {

    constructor(page) {
        this.page = page
    }

    /**
     * @param shortTypeName property type FQCN, without the `net.nemerosa.ontrack.extension.` prefix,
     *                      eg. `general.AutoPromotionPropertyType`
     */
    property(shortTypeName) {
        return this.page.getByTestId(`property-${shortTypeName}`)
    }

    /**
     * Opens the edit dialog for the given property.
     */
    async editProperty(shortTypeName) {
        const property = this.property(shortTypeName)
        await expect(property).toBeVisible()
        const editButton = property.getByTestId('property-edit')
        await expect(editButton).toBeVisible()
        await editButton.click()
        const dialog = new PropertyDialog(this.page)
        await dialog.checkOnDialog()
        return dialog
    }
}
