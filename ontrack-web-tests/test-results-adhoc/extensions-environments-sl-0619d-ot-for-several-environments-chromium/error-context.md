# Test info

- Name: creating a slot for several environments
- Location: /Users/damien/Documents/workspaces/yontrack/yontrack/.claude/worktrees/usd-eur-conversion-b41813/ontrack-web-tests/tests/extensions/environments/slots.spec.js:8:5

# Error details

```
Error: Timed out 30000ms waiting for expect(locator).toBeVisible()

Locator: getByRole('textbox', { name: 'Username' })
Expected: visible
Received: <element(s) not found>
Call log:
  - expect.toBeVisible with timeout 30000ms
  - waiting for getByRole('textbox', { name: 'Username' })

    at login (/Users/damien/Documents/workspaces/yontrack/yontrack/.claude/worktrees/usd-eur-conversion-b41813/ontrack-web-tests/tests/core/login.js:27:33)
    at /Users/damien/Documents/workspaces/yontrack/yontrack/.claude/worktrees/usd-eur-conversion-b41813/ontrack-web-tests/tests/extensions/environments/slots.spec.js:18:5
```

# Page snapshot

```yaml
- banner: ontrack
- main:
  - heading "We are sorry..." [level=1]
  - paragraph: HTTPS required
```

# Test source

```ts
   1 | import {expect} from "@playwright/test";
   2 | import {selectUserMenu} from "./userMenu";
   3 |
   4 | export const login = async (
   5 |     page,
   6 |     ontrack,
   7 |     customerUsername = undefined,
   8 |     customPassword = undefined,
   9 |     options = {}
  10 | ) => {
  11 |     let username
  12 |     let password
  13 |     if (customerUsername) {
  14 |         username = customerUsername
  15 |         password = customPassword
  16 |     } else {
  17 |         const creds = ontrack.connection.credentials
  18 |         username = creds.username
  19 |         password = creds.password
  20 |     }
  21 |     await page.goto(ontrack.connection.ui)
  22 |     // We expect to land on the sign-in page
  23 |     const signIn = await signInButton(page)
  24 |     await signIn.click()
  25 |     // Filling the username and password
  26 |     const usernameField = page.getByRole("textbox", {exact: false, name: "Username"});
> 27 |     await expect(usernameField).toBeVisible()
     |                                 ^ Error: Timed out 30000ms waiting for expect(locator).toBeVisible()
  28 |     await usernameField.fill(username)
  29 |     await page.getByRole("textbox", {exact: false, name: "Password"}).fill(password)
  30 |     // Launching the login
  31 |     await page.getByRole("button", {name: "Sign In", exact: true}).click()
  32 |
  33 |     // If we expect a specific element to be there once signed in
  34 |     if (options.ready) {
  35 |         return expect(options.ready(page)).toBeVisible()
  36 |     }
  37 |     // If we expect a message
  38 |     if (options.message) {
  39 |         return expect(page.getByText(options.message)).toBeVisible()
  40 |     }
  41 |     // We expect to be on the Next UI home page now
  42 |     else {
  43 |         return expect(page.getByText("Dashboard", {exact: true})).toBeVisible()
  44 |     }
  45 | };
  46 |
  47 | export const logout = async (page) => {
  48 |     // Selecting the logout menu
  49 |     await selectUserMenu(page, "Sign out")
  50 |     // We expect to be on the login page again
  51 |     await signInButton(page)
  52 | }
  53 |
  54 | /**
  55 |  * The identity provider's button on the sign-in page, once it is there.
  56 |  *
  57 |  * Exported so a test that has just signed *out* can assert it has landed
  58 |  * somewhere it can sign back in from, and drive it without `login` navigating
  59 |  * somewhere of its own first - where the sign-out landed is frequently the very
  60 |  * thing being asserted.
  61 |  */
  62 | export const signInButton = async (page) => {
  63 |     let button = page.getByRole("button", {name: "Sign in", exact: false});
  64 |     await expect(button).toBeVisible()
  65 |     await page.waitForTimeout(500)
  66 |     return button
  67 | }
  68 |
```