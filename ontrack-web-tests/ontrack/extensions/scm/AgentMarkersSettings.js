import {AbstractSettings} from "@ontrack/AbstractSettings";
import {graphQLCall} from "@ontrack/graphql";
import {gql} from "graphql-request";

export class AgentMarkersSettings extends AbstractSettings {
    constructor(ontrack) {
        super(ontrack)
    }

    async saveSettings({builtInConventions = true, patterns = []} = {}) {
        return this.doSaveSettings({
            id: 'agent-markers',
            values: {
                builtInConventions,
                patterns,
            }
        })
    }

    async getSettings() {
        const data = await graphQLCall(
            this.ontrack.connection,
            gql`
                query AgentMarkersSettings {
                    settings {
                        settingsById(id: "agent-markers") {
                            values
                        }
                    }
                }
            `
        )
        return data.settings.settingsById.values
    }
}
