const {login} = require("../../../core/login");
const {HttpConfigurationsPage} = require("./HttpConfigurationsPage");
const {generate} = require("@ontrack/utils");
const {test} = require("../../../fixtures/connection");

test('creation of an HTTP configuration with a bearer token', async ({page, ontrack}) => {
    await login(page, ontrack)
    const httpConfigurationsPage = new HttpConfigurationsPage(page, ontrack)
    await httpConfigurationsPage.goTo()

    const name = generate("http-")
    const url = `https://${name}.example.com/api`
    await httpConfigurationsPage.createConfig({
        name,
        url,
        authenticationType: "Bearer token authentication",
        fill: async (page) => {
            await page.getByLabel("Token").fill("some-token")
        },
    })

    await httpConfigurationsPage.checkConfiguration({name, url, authenticationType: "Bearer token"})
})

test('creation of an HTTP configuration without authentication', async ({page, ontrack}) => {
    await login(page, ontrack)
    const httpConfigurationsPage = new HttpConfigurationsPage(page, ontrack)
    await httpConfigurationsPage.goTo()

    const name = generate("http-")
    const url = `https://${name}.example.com`
    await httpConfigurationsPage.createConfig({name, url})

    await httpConfigurationsPage.checkConfiguration({name, url, authenticationType: "No authentication"})
})
