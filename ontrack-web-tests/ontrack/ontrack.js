import {createProject, getProjectById, projectList} from "@ontrack/project";
import {getValidationRunById} from "@ontrack/validationRun";
import {admin} from "@ontrack/admin";
import {OntrackConfigurations} from "@ontrack/configurations";
import {getBranchById} from "@ontrack/branch";
import {EnvironmentsExtension} from "@ontrack/extensions/environments/environments";
import {OntrackSettings} from "@ontrack/settings";
import {AutoVersioningExtension} from "@ontrack/extensions/auto-versioning/AutoVersioningExtension";
import {SearchMgt} from "@ontrack/search";
import {labels} from "@ontrack/labels";

/**
 * Ontrack service
 */
export class Ontrack {
    constructor(connection) {
        this.connection = connection
    }

    admin = () => admin(this)
    configurations = new OntrackConfigurations(this)
    settings = new OntrackSettings(this)
    search = new SearchMgt(this)

    labels = () => labels(this)

    createProject = async (name) => createProject(this, name)
    getProjectById = async (id) => getProjectById(this, id)
    projectList = async () => projectList(this)

    getBranchById = async (id) => getBranchById(this, id)

    getValidationRunById = async (runId) => getValidationRunById(this, runId)

    // Extensions

    environments = new EnvironmentsExtension(this)
    autoVersioning = new AutoVersioningExtension(this)

    // Cloning with a specific token
    withToken = (token) => new Ontrack(
        this.connection.withToken(token)
    )
}
