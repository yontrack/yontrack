import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";
import {gqlValidationChipStamp} from "@components/primitives/ValidationChipFragments";
import {gqlSignatureActorFields} from "@components/common/actors/actors";
import {gqlAssistedChangeFields} from "@components/extension/scm/assistants/assistants";

export const gqlBuilds = gql`
    query LoadBuilds(
        $branchId: Int!,
        $offset: Int!,
        $size: Int!,
        $filterType: String,
        $filterData: String,
    ) {
        branches(id: $branchId) {
            buildsPaginated(
                offset: $offset,
                size: $size,
                generic: {
                    type: $filterType,
                    data: $filterData
                }
            ) {
                pageInfo {
                    totalSize
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    id
                    key: id
                    name
                    creation {
                        time
                        ${gqlSignatureActorFields}
                    }
                    ${gqlAssistedChangeFields}
                    decorations {
                        ...decorationContent
                    }
                    promotionRuns(lastPerLevel: true) {
                        id
                        creation {
                            time
                            user
                            ${gqlSignatureActorFields}
                        }
                        description
                        annotatedDescription
                        fieldValues {
                            name
                            value
                        }
                        build {
                            id
                            name
                        }
                        promotionLevel {
                            id
                            name
                            description
                            image
                            fields {
                                name
                                displayName
                                type
                            }
                        }
                        authorizations {
                            name
                            action
                            authorized
                        }
                    }
                    validations {
                        validationStamp {
                            ...ValidationChipStamp
                            description
                            annotatedDescription
                        }
                        validationRuns(count: 1) {
                            id
                            runInfo {
                                runTime
                                sourceUri
                            }
                            lastStatus {
                                creation {
                                    time
                                    user
                                    ${gqlSignatureActorFields}
                                }
                                description
                                annotatedDescription
                                statusID {
                                    id
                                    name
                                }
                            }
                        }
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                }
            }
        }
    }
    
    ${gqlDecorationFragment}
    ${gqlValidationChipStamp}
`