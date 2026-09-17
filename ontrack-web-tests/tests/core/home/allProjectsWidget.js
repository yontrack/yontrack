import {expect} from "@playwright/test";
import {gql} from "graphql-request";
import {graphQLCallMutation} from "@ontrack/graphql";
import {labelDisplay} from "../../support/labels";

const applyDashboardsMutation = gql`
    mutation ApplyDashboards($yaml: String!) {
        applyDashboards(input: { yaml: $yaml }) {
            dashboards { uuid name }
            errors { message }
        }
    }
`

/**
 * The "All projects" widget of the home page, and its filter.
 *
 * The widget is not part of the default dashboard, so the page object brings its own dashboard
 * and selects it - which also makes the test independent from the dashboard the user happens to
 * have selected last.
 */
export class AllProjectsWidget {

    constructor(page, ontrack) {
        this.page = page
        this.ontrack = ontrack
        this.dashboardName = `all-projects-${Date.now()}`
    }

    /**
     * Creates the dashboard made of the widget alone, through the API.
     *
     * It is called **before** signing in: the home page loads the list of dashboards once, so a
     * dashboard created after that is not in the menu the test then opens.
     */
    async createDashboard() {
        const yaml = [
            `- name: "${this.dashboardName}"`,
            `  widgets:`,
            `    - key: "home/AllProjectList"`,
            `      layout: {x: 0, y: 0, w: 12, h: 25}`,
            `      config: {}`,
        ].join('\n')
        await graphQLCallMutation(
            this.ontrack.connection,
            'applyDashboards',
            applyDashboardsMutation,
            {yaml}
        )
    }

    /**
     * Selects the dashboard from the menu of the home page, the test being signed in.
     */
    async select() {
        await this.page.getByRole('button', {name: 'Dashboard', exact: true}).click()
        await this.page.getByText(this.dashboardName).click()
        await expect(this.page.getByText('All projects', {exact: true})).toBeVisible()
    }

    /**
     * Filters on a fragment of the project name, like a user typing it and pressing Enter.
     */
    async filterByName(name) {
        const input = this.page.getByPlaceholder('Project name')
        await input.fill(name)
        await input.press('Enter')
    }

    /**
     * Adds a label to the filter. The labels add up, and a project must carry them all to be
     * listed.
     */
    async filterByLabel(label) {
        const filter = this.page.getByTestId('project-labels-filter')
        // The search box of the select is read-only until the select has the focus
        await filter.click()
        const input = filter.locator('input')
        await input.fill(labelDisplay(label))
        // The first - and, the display string being unique, the only - matching option
        await input.press('Enter')
        // The dropdown stays open and would cover the list
        await this.page.keyboard.press('Escape')
    }

    projectLink(project) {
        return this.page.getByRole('link', {name: project.name, exact: true})
    }

    async expectProject(project) {
        await expect(this.projectLink(project)).toBeVisible()
    }

    async expectNoProject(project) {
        await expect(this.projectLink(project)).not.toBeVisible()
    }

    chip(label) {
        return this.page.getByTestId(`label-${labelDisplay(label)}`)
    }
}
