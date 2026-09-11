import {expect} from "@playwright/test";
import {selectUserMenu} from "./userMenu";

export const login = async (
    page,
    ontrack,
    customerUsername = undefined,
    customPassword = undefined,
    options = {}
) => {
    let username
    let password
    if (customerUsername) {
        username = customerUsername
        password = customPassword
    } else {
        const creds = ontrack.connection.credentials
        username = creds.username
        password = creds.password
    }
    await page.goto(ontrack.connection.ui)
    // We expect to land on the sign-in page
    const signIn = await signInButton(page)
    await signIn.click()
    // Filling the username and password
    const usernameField = page.getByRole("textbox", {exact: false, name: "Username"});
    await expect(usernameField).toBeVisible()
    await usernameField.fill(username)
    await page.getByRole("textbox", {exact: false, name: "Password"}).fill(password)
    // Launching the login
    await page.getByRole("button", {name: "Sign In", exact: true}).click()

    // If we expect a specific element to be there once signed in
    if (options.ready) {
        return expect(options.ready(page)).toBeVisible()
    }
    // If we expect a message
    if (options.message) {
        return expect(page.getByText(options.message)).toBeVisible()
    }
    // We expect to be on the Next UI home page now
    else {
        return expect(page.getByText("Dashboard", {exact: true})).toBeVisible()
    }
};

export const logout = async (page) => {
    // Selecting the logout menu
    await selectUserMenu(page, "Sign out")
    // We expect to be on the login page again
    await signInButton(page)
}

/**
 * The identity provider's button on the sign-in page, once it is there.
 *
 * Exported so a test that has just signed *out* can assert it has landed
 * somewhere it can sign back in from, and drive it without `login` navigating
 * somewhere of its own first - where the sign-out landed is frequently the very
 * thing being asserted.
 */
export const signInButton = async (page) => {
    let button = page.getByRole("button", {name: "Sign in", exact: false});
    await expect(button).toBeVisible()
    await page.waitForTimeout(500)
    return button
}
