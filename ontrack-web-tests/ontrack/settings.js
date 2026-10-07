import {GitHubIngestionSettings} from "@ontrack/extensions/github/GitHubIngestionSettings";
import {AgentMarkersSettings} from "@ontrack/extensions/scm/AgentMarkersSettings";

export class OntrackSettings {
    constructor(ontrack) {
        this.ontrack = ontrack
        this.gitHubIngestion = new GitHubIngestionSettings(ontrack)
        this.agentMarkers = new AgentMarkersSettings(ontrack)
    }
}
