// @ts-check
const {login} = require("../../core/login");
const {BuildPage} = require("../../core/builds/BuildPage");
const {provisionChangeLog, commits, SCMChangeLogPage} = require("./scm");
const {test} = require("../../fixtures/connection");
const {expect} = require("@playwright/test");

test("build page change log command opens the change log since the previous build", async ({page, ontrack}) => {
    const {to, builds} = await provisionChangeLog(ontrack)
    // The build immediately before `to` on its branch
    const previous = builds[builds.length - 2]

    await login(page, ontrack)
    const buildPage = new BuildPage(page, to)
    await buildPage.goTo()

    const command = buildPage.changeLogCommand()
    await expect(command).toBeVisible()
    await expect(command).toHaveAttribute('title', `Change log since ${previous.name}`)
    await command.getByRole('link', {name: 'Change log'}).click()

    await expect(page).toHaveURL(new RegExp(`/extension/scm/changelog\\?from=${previous.id}&to=${to.id}$`))
    const changeLogPage = new SCMChangeLogPage(page, ontrack)
    await changeLogPage.checkDisplayed()
    await changeLogPage.checkBuildFrom(previous)
    await changeLogPage.checkBuildTo(to)
    await changeLogPage.checkCommitMessage(commits[4], {present: true})
    await changeLogPage.checkCommitMessage(commits[3], {present: false})
})

test("build page change log command is absent for the first build of a branch", async ({page, ontrack}) => {
    const {from} = await provisionChangeLog(ontrack)

    await login(page, ontrack)
    const buildPage = new BuildPage(page, from)
    await buildPage.goTo()

    // Waiting for the commands to be rendered before asserting an absence
    await buildPage.expectNextBuild({visible: true})
    await expect(buildPage.changeLogCommand()).toHaveCount(0)
})

test("build page change log command is absent for a branch without SCM", async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    await branch.createBuild()
    const build = await branch.createBuild()

    await login(page, ontrack)
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // The build has a previous build: only the missing SCM hides the command
    await buildPage.expectPreviousBuild({visible: true})
    await expect(buildPage.changeLogCommand()).toHaveCount(0)
})
