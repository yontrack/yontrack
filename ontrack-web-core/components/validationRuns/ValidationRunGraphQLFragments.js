import {gql} from "graphql-request";

export const gqlValidationRunContent = gql`
    fragment ValidationRunContent on ValidationRun {
        id
        runOrder
        data {
            descriptor {
                id
                feature {
                    id
                }
            }
            data
        }
        authorizations {
            name
            action
            authorized
        }
        runInfo {
            sourceType
            sourceUri
            triggerType
            triggerData
            runTime
        }
        lastStatus {
            statusID {
                id
                name
            }
        }
        validationRunStatuses {
            id
            creation {
                user
                time
            }
            description
            annotatedDescription
            statusID {
                id
                name
            }
            authorizations {
                name
                action
                authorized
            }
        }
    }
`

export const gqlValidationRunTableContent = gql`
    fragment ValidationRunTableContent on ValidationRun {
        id
        key: id
        runOrder
        runInfo {
            runTime
            sourceType
            sourceUri
            triggerType
            triggerData
        }
        lastStatus {
            creation {
                time
                user
            }
            description
            annotatedDescription
            statusID {
                id
                name
            }
        }
        validationStamp {
            id
            name
            image
            description
            annotatedDescription
            # Feeds the fallback glyph on a ValidationChip when the stamp has no
            # uploaded image - see ValidationDataTypeGlyphs.
            dataType {
                descriptor {
                    id
                }
            }
        }
        data {
            descriptor {
                feature {
                    id
                }
                id
            }
            data
        }
    }
`