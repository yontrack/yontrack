import {gql} from "graphql-request";
import {graphQLCall, graphQLCallMutation} from "@ontrack/graphql";
import {generate} from "@ontrack/utils";

const gqlLabelData = gql`
    fragment LabelData on Label {
        id
        category
        name
        description
        color
    }
`

export class LabelsMgt {

    constructor(ontrack) {
        this.ontrack = ontrack
    }

    /**
     * Creates a label. Both the category and the name default to a generated
     * value, so that two tests running against the same instance never collide.
     */
    async createLabel({category = undefined, name, description, color = '#FF0000'} = {}) {
        // `category: null` is a label without a category, which is not the same as not saying
        // anything about the category at all
        const actualCategory = category === undefined ? generate('cat-') : category
        const data = await graphQLCallMutation(
            this.ontrack.connection,
            'createLabel',
            gql`
                mutation CreateLabel(
                    $category: String,
                    $name: String!,
                    $description: String,
                    $color: String!,
                ) {
                    createLabel(input: {
                        category: $category,
                        name: $name,
                        description: $description,
                        color: $color,
                    }) {
                        label {
                            ...LabelData
                        }
                        errors {
                            message
                        }
                    }
                }
                ${gqlLabelData}
            `,
            {
                category: actualCategory,
                name: name ?? generate('lbl-'),
                description: description ?? null,
                color,
            }
        )
        return data.createLabel.label
    }

    async deleteLabel(id) {
        await graphQLCallMutation(
            this.ontrack.connection,
            'deleteLabel',
            gql`
                mutation DeleteLabel($id: Int!) {
                    deleteLabel(input: {id: $id}) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {id: Number(id)}
        )
    }

    /**
     * Sets the labels of a project, replacing the existing ones.
     */
    async setProjectLabels(projectId, labelIds) {
        await graphQLCallMutation(
            this.ontrack.connection,
            'setProjectLabels',
            gql`
                mutation SetProjectLabels($projectId: Int!, $labelIds: [Int!]!) {
                    setProjectLabels(input: {
                        projectId: $projectId,
                        labelIds: $labelIds,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            {projectId: Number(projectId), labelIds: labelIds.map(Number)}
        )
    }

    /**
     * Labels matching a category and a name, used to check what the UI has
     * actually saved.
     */
    async findLabels({category, name}) {
        const data = await graphQLCall(
            this.ontrack.connection,
            gql`
                query FindLabels($category: String, $name: String) {
                    labels(category: $category, name: $name) {
                        ...LabelData
                        projectCount
                    }
                }
                ${gqlLabelData}
            `,
            {category, name}
        )
        return data.labels
    }
}

export const labels = (ontrack) => new LabelsMgt(ontrack)
