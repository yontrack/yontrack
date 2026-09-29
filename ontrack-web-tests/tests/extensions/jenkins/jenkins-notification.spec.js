// @ts-check
const {expect} = require('@playwright/test')
const {generate} = require("@ontrack/utils");
const {login} = require("../../core/login");
const {ProjectPage} = require("../../core/projects/project");
const {SubscriptionsPage} = require("../notifications/subscriptions");
const {test} = require("../../fixtures/connection");

test('displaying the configuration of a Jenkins notification', async ({page, ontrack}) => {
    // Provisioning
    // 1. Creating a Jenkins configuration
    const configName = generate("jenkins-")
    await ontrack.configurations.jenkins.createConfig({
        name: configName,
        url: `mock://jenkins.${configName}`,
        user: "",
        password: "",
    })
    // 2. Project to receive notifications for
    const project = await ontrack.createProject()
    // 3. Creating a subscription for this project calling a Jenkins job on new branches
    const jobName = generate("pipeline/")
    await project.subscribe({
        name: "Jenkins on new branches",
        events: ["new_branch"],
        channel: "jenkins",
        channelConfig: {
            config: configName,
            job: jobName,
            callMode: 'ASYNC',
        }
    })

    // Login
    await login(page, ontrack)
    // Navigating to the project
    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()
    // Navigating to the project's subscriptions page
    const subscriptionsPage = new SubscriptionsPage(page)
    await subscriptionsPage.selectToolsSubscriptions()
    // Expecting the subscription to be visible
    await expect(page.getByText("When a branch is created")).toBeVisible()
    // Details of the subscription
    await expect(page.getByText(`mock://jenkins.${configName}`)).toBeVisible()
    await expect(page.getByText('Asynchronous')).toBeVisible()
})