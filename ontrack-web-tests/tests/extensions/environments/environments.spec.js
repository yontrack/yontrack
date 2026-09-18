const {login} = require("../../core/login");
const {HomePage} = require("../../core/home/home");
const {generate} = require("@ontrack/utils");
const {test} = require("../../fixtures/connection");

/**
 * Creating an environment, and the rule the matrix is built on: **a column exists only where a
 * visible row has a slot**.
 *
 * The two halves belong in one test because the first is not observable without the second. On the
 * old environments list a new environment was a card of its own straight away; on the matrix it is a
 * column, and a column no project can fill is noise on a screen that is already wide. So the
 * environment is created, checked to be absent, given a slot, and checked to be there.
 */
test('creating an environment, which becomes a column once something has a slot in it', async ({page, ontrack}) => {
    const project = await ontrack.createProject()

    await login(page, ontrack)
    const homePage = new HomePage(page, ontrack)
    const environmentsPage = await homePage.selectEnvironments()

    // "New environment" moved off the header and behind Setup
    const name = generate("env-")
    await environmentsPage.createEnvironment({
        name: name,
        description: `Description for ${name}`,
        order: 100,
        tags: ['test'],
    })

    // All projects, narrowed to this one: the matrix pages twenty projects at a time and the stack
    // is shared, so without the search this project's row may simply not be on the first page.
    await environmentsPage.selectScope('All')
    await environmentsPage.searchProject(project.name)

    const environment = await ontrack.environments.findEnvironmentByName(name)
    await environmentsPage.checkEnvironmentIsNotVisible(environment)

    // ...and now it has something to show
    await environmentsPage.createSlot({
        projectName: project.name,
        qualifier: '',
        description: "Slot",
        environmentNames: [name],
    })
    await environmentsPage.checkEnvironmentIsVisible(name)
    await environmentsPage.checkProjectIsVisible(project)
})
