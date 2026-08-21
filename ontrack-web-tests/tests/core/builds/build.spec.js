const {login} = require("../login");
const {BuildPage} = require("../builds/BuildPage");
const {BranchPage} = require("../branches/branch");
const {test} = require("../../fixtures/connection");
const {generate} = require("@ontrack/utils");

test('build page', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    // Login
    await login(page, ontrack)
    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
})

test('build display URL', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    await login(page, ontrack)

    await page.goto(`${ontrack.connection.ui}/display/build/${project.name}/${branch.name}/${build.name}`)

    const buildPage = new BuildPage(page, build)
    await buildPage.checkOnBuildPage()
})

test('build display URL using release', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    const release = generate("release-")
    await build.setRelease(release)

    await login(page, ontrack)

    await page.goto(`${ontrack.connection.ui}/display/build/${project.name}/${branch.name}/${release}`)

    const buildPage = new BuildPage(page, build)
    await buildPage.checkOnBuildPage()
})

test('build page with links', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    // Link
    const targetProject = await ontrack.createProject()
    const targetBranch = await targetProject.createBranch()
    const target = await targetBranch.createBuild()
    await build.linkTo(target)

    // Login
    await login(page, ontrack)
    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
})

test('build page with validations without a type', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const validationStamp = await branch.createValidationStamp()
    const build = await branch.createBuild()
    await build.validate(validationStamp, {status: "FAILED"})

    // Login
    await login(page, ontrack)
    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
})

test('graph of links between builds', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    // Link
    const targetProject = await ontrack.createProject()
    const targetBranch = await targetProject.createBranch()
    const target = await targetBranch.createBuild()
    await build.linkTo(target)

    // Login
    await login(page, ontrack)

    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // Navigating to the links
    const buildLinks = await buildPage.goToLinks()

    // We expect the graph view
    await buildLinks.expectOnGraphView()
    await buildLinks.expectBuildGraphNodeVisible(build)
    await buildLinks.expectBuildGraphNodeVisible(target)

    // Switching to the tree view
    await buildLinks.switchView()
    await buildLinks.expectOnTreeView()
    await buildLinks.expectBuildTreeNodeVisible(build)
    await buildLinks.expectBuildTreeNodeVisible(target)

    // Switching to the graph view again
    await buildLinks.switchView()
    await buildLinks.expectOnGraphView()
    await buildLinks.expectBuildGraphNodeVisible(build)
    await buildLinks.expectBuildGraphNodeVisible(target)
})

test('closed tree view alert stays closed after a reload', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()

    // Link, so that the links page has something to display
    const targetProject = await ontrack.createProject()
    const targetBranch = await targetProject.createBranch()
    const target = await targetBranch.createBuild()
    await build.linkTo(target)

    // Login
    await login(page, ontrack)

    // Navigating to the links, in tree view
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()
    const buildLinks = await buildPage.goToLinks()
    await buildLinks.switchView()
    await buildLinks.expectOnTreeView()

    // The alert is displayed
    await buildLinks.expectTreeViewAlert({visible: true})

    // A reload on its own does not hide it
    await page.reload()
    await buildLinks.expectOnTreeView()
    await buildLinks.expectTreeViewAlert({visible: true})

    // Closing it hides it
    await buildLinks.closeTreeViewAlert()
    await buildLinks.expectTreeViewAlert({visible: false})

    // After a reload, the tree view is restored (also from the local storage)...
    await page.reload()
    await buildLinks.expectOnTreeView()
    await buildLinks.expectBuildTreeNodeVisible(build)

    // ... and the alert stays closed
    await buildLinks.expectTreeViewAlert({visible: false})
})

test('deleting a build', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild()
    // Login
    await login(page, ontrack)
    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // Deleting the build
    await buildPage.deleteBuild()

    // Checking we are on the branch page
    const branchPage = new BranchPage(page, branch)
    await branchPage.checkOnPage()
})

test('updating a build', async ({page, ontrack}) => {
    // Provisioning
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build = await branch.createBuild('initial-name')

    // Login
    await login(page, ontrack)
    // Navigating to the build
    const buildPage = new BuildPage(page, build)
    await buildPage.goTo()

    // Editing the build
    await buildPage.update({
        name: 'new-name',
        description: 'Some new description',
    })

    // Checking that the page has been updated
    await buildPage.assertName('new-name')
    await buildPage.assertDescription('Some new description')
})

test('previous and next builds', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    const branch = await project.createBranch()
    const build1 = await branch.createBuild('1.0.1')
    const build2 = await branch.createBuild('1.0.2')
    const build3 = await branch.createBuild('1.0.3')

    // Going to build 1
    await login(page, ontrack)
    const buildPage1 = new BuildPage(page, build1)
    await buildPage1.goTo()
    await buildPage1.expectPreviousBuild({visible: false})
    await buildPage1.expectNextBuild({visible: true})

    // Navigating to build 2
    await buildPage1.nextBuild()
    const buildPage2 = new BuildPage(page, build2)
    await buildPage2.checkOnBuildPage()
    await buildPage2.expectPreviousBuild({visible: true})
    await buildPage2.expectNextBuild({visible: true})

    // Navigating to build 3
    await buildPage2.nextBuild()
    const buildPage3 = new BuildPage(page, build3)
    await buildPage3.checkOnBuildPage()
    await buildPage3.expectPreviousBuild({visible: true})
    await buildPage3.expectNextBuild({visible: false})

    // Navigating again to build 2
    await buildPage3.previousBuild()
    await buildPage2.checkOnBuildPage()
    await buildPage2.expectPreviousBuild({visible: true})
    await buildPage2.expectNextBuild({visible: true})
})

test('filtering the downstream links', async ({page, ontrack}) => {
    // Source build
    const sourceProject = await ontrack.createProject()
    const sourceBranch = await sourceProject.createBranch()
    const sourceBuild = await sourceBranch.createBuild()
    // Target builds (==> downstream)
    const targetBuilds = []
    for (const i of [1, 2, 3]) {
        const targetProject = await ontrack.createProject()
        const targetBranch = await targetProject.createBranch()
        const targetBuild = await targetBranch.createBuild()
        await sourceBuild.linkTo(targetBuild)
        targetBuilds.push(targetBuild)
    }

    // Going to the source build page
    await login(page, ontrack)
    const buildPage = new BuildPage(page, sourceBuild)
    await buildPage.goTo()

    // Getting access to the downstream links
    const links = await buildPage.getDownstreamLinks()

    // Checks all builds are visible
    await links.isProjectLinkVisible({project: targetBuilds[0].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: targetBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: targetBuilds[2].branch.project.name, visible: true})

    // Filtering on the project name
    await links.filterByProjectName({project: targetBuilds[1].branch.project.name})
    await links.isProjectLinkVisible({project: targetBuilds[0].branch.project.name, visible: false})
    await links.isProjectLinkVisible({project: targetBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: targetBuilds[2].branch.project.name, visible: false})

    // Clearing the filter
    await links.clearFilterByProjectName()
    await links.isProjectLinkVisible({project: targetBuilds[0].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: targetBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: targetBuilds[2].branch.project.name, visible: true})
})

test('filtering the upstream links', async ({page, ontrack}) => {
    // Target build
    const targetProject = await ontrack.createProject()
    const targetBranch = await targetProject.createBranch()
    const targetBuild = await targetBranch.createBuild()
    // Source builds (==> upstream)
    const sourceBuilds = []
    for (const i of [1, 2, 3]) {
        const sourceProject = await ontrack.createProject()
        const sourceBranch = await sourceProject.createBranch()
        const sourceBuild = await sourceBranch.createBuild()
        await sourceBuild.linkTo(targetBuild)
        sourceBuilds.push(sourceBuild)
    }

    // Going to the target build page
    await login(page, ontrack)
    const buildPage = new BuildPage(page, targetBuild)
    await buildPage.goTo()

    // Getting access to the upstream links
    const links = await buildPage.getUpstreamLinks()

    // Checks all builds are visible
    await links.isProjectLinkVisible({project: sourceBuilds[0].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: sourceBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: sourceBuilds[2].branch.project.name, visible: true})

    // Filtering on the project name
    await links.filterByProjectName({project: sourceBuilds[1].branch.project.name})
    await links.isProjectLinkVisible({project: sourceBuilds[0].branch.project.name, visible: false})
    await links.isProjectLinkVisible({project: sourceBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: sourceBuilds[2].branch.project.name, visible: false})

    // Clearing the filter
    await links.clearFilterByProjectName()
    await links.isProjectLinkVisible({project: sourceBuilds[0].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: sourceBuilds[1].branch.project.name, visible: true})
    await links.isProjectLinkVisible({project: sourceBuilds[2].branch.project.name, visible: true})
})
