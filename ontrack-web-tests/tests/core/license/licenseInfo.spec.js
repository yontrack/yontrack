import {expect} from "@playwright/test";
import {test} from "../../fixtures/connection";
import {login} from "../login";

/**
 * The license page shows a card per licensed feature. A feature with no data has no
 * `license-feature-data/<featureId>/Info` component, and its card must not fail to load one.
 */
test('license page renders every licensed feature', async ({page, ontrack}) => {
    await login(page, ontrack)
    await page.goto(`${ontrack.connection.ui}/extension/license/info`)

    // The acceptance stack runs with the development licence, which enables every feature
    for (const name of ["Agent governance", "Audit trail", "Delivery scorecard", "Environments & deployment pipelines"]) {
        await expect(page.getByText(name, {exact: true})).toBeVisible()
    }
    // A component which fails to load shows an inline "Error"
    await expect(page.getByText("Error", {exact: true})).toHaveCount(0)
})
