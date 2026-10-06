// @ts-check
const {expect} = require("@playwright/test");
const {login, logout, signInButton} = require("./login");
const {test} = require("../fixtures/connection");

test('login', {tag: "@auth"}, async ({page, ontrack}) => {
    await login(page, ontrack)
})

test('login and logout', {tag: "@auth"}, async ({page, ontrack}) => {
    await login(page, ontrack)
    await logout(page)
    // Signing out ends the identity provider's session too (#1734): signing back in asks for the
    // credentials again, instead of the provider answering silently. Run under `@auth` on the
    // three legs - the `keycloak` provider's back-channel on `main` and `ldap`, the generic `oidc`
    // provider's front-channel on `oidc`.
    await (await signInButton(page)).click()
    await expect(page.getByRole("textbox", {exact: false, name: "Username"})).toBeVisible()
})
