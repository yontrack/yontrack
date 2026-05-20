const {test} = require("../../fixtures/connection");
const {generate} = require("@ontrack/utils");
const {createMockSCMContext} = require("@ontrack/extensions/scm/scm");
const {login} = require("../../core/login");
const {BranchPage} = require("../../core/branches/branch");

test('auto-versioning GitHub post-processing config is displayed', async ({page, ontrack}) => {
    // Create dependency project — only needs to exist as an AV source
    const depProject = await ontrack.createProject(generate("dep-"))
    const depBranch = await depProject.createBranch("main")
    await depBranch.createPromotionLevel("GOLD")

    // Create target project with Mock SCM
    const targetProject = await ontrack.createProject(generate("target-"))
    const targetBranch = await targetProject.createBranch("main")
    const mockSCMContext = createMockSCMContext(ontrack)
    await mockSCMContext.configureProjectForMockSCM(targetProject)
    await mockSCMContext.configureBranchForMockSCM(targetBranch)
    await mockSCMContext.repositoryFile({path: "versions.properties", content: 'version=1.0.0'})

    // Configure auto-versioning with GitHub post-processing
    await ontrack.autoVersioning.setAutoVersioningConfig(targetBranch, {
        sourceProject: depProject.name,
        sourceBranch: 'main',
        sourcePromotion: 'GOLD',
        targetPath: 'versions.properties',
        targetProperty: 'version',
        postProcessing: 'github',
        postProcessingConfig: {
            dockerImage: 'docker/my-image:latest',
            dockerCommand: 'echo "upgrading"',
            commitMessage: 'Upgrade version to {version}',
            workflow: 'post-processing.yml',
        },
    })

    // Navigate to the AV config page on the target branch
    await login(page, ontrack)
    const branchPage = new BranchPage(page, targetBranch)
    await branchPage.goTo()
    const avConfigPage = await branchPage.navigateToAutoVersioningConfig()

    // Expand the config row for the dependency project
    const avConfigDetails = await avConfigPage.displayConfig(depProject.name)

    // Verify GitHub post-processing fields are displayed correctly
    await avConfigDetails.expectGitHubPostProcessing({
        workflow: 'post-processing.yml',
        dockerImage: 'docker/my-image:latest',
        dockerCommand: 'echo "upgrading"',
        commitMessage: 'Upgrade version to {version}',
    })
})
