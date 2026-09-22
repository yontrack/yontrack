import {expect} from "@playwright/test";

export const confirmBox = async (page, title, options = {okText: "OK"}) => {
    // The visible one: antd 6's confirm renders its title twice, once as the dialog's label and once
    // as the visible heading, so the text alone matches two elements
    await expect(page.getByText(title, {exact: true}).filter({visible: true}).first()).toBeVisible()
    const okButton = page.getByRole("button", {name: options.okText, exact: true})
    await okButton.click()
    await expect(okButton).not.toBeVisible()
}
