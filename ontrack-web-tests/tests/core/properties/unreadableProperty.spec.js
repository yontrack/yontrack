import {test} from "../../fixtures/connection";
import {login} from "../login";
import {ProjectPage} from "../projects/project";

const MESSAGE = 'general.MessagePropertyType'
const ERROR = 'Parameter specified as non-null is null: authType'

test('a property whose value cannot be read is displayed with its error and can be deleted', async ({page, ontrack}) => {
    const project = await ontrack.createProject()
    await project.setProperty(`net.nemerosa.ontrack.extension.${MESSAGE}`, {
        type: 'INFO',
        text: 'Readable message',
    })

    // The UI tests cannot store a value which the server then fails to read (deleting a configuration
    // deletes the properties using it): the property, actually stored, is returned as unreadable,
    // the way the server returns it (no value, an error)
    await page.route('**/api/protected/graphql', async (route) => {
        const body = route.request().postDataJSON()
        if (!body?.query?.includes('query EntityProperties(')) {
            return route.continue()
        }
        const response = await route.fetch()
        const json = await response.json()
        const data = json.data ?? json
        data.entity.properties
            .filter(it => it.type.typeName === `net.nemerosa.ontrack.extension.${MESSAGE}` && it.value)
            .forEach(it => {
                it.value = null
                it.error = ERROR
            })
        await route.fulfill({response, json})
    })

    await login(page, ontrack)

    const projectPage = new ProjectPage(page, ontrack, project)
    await projectPage.goTo()

    const properties = await projectPage.openProperties()
    await properties.checkPropertyError(MESSAGE, ERROR)

    // Deleted for real on the server
    await properties.deleteProperty(MESSAGE)
})
