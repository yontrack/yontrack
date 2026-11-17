import {JIRAConfigurations} from "@ontrack/extensions/jira/jira";
import {JenkinsConfigurations} from "@ontrack/extensions/jenkins/jenkins";
import {HttpConfigurations} from "@ontrack/extensions/notifications/http";

export class OntrackConfigurations {

    constructor(ontrack) {
        this.ontrack = ontrack
        this.jira = new JIRAConfigurations(ontrack)
        this.jenkins = new JenkinsConfigurations(ontrack)
        this.http = new HttpConfigurations(ontrack)
    }

}
