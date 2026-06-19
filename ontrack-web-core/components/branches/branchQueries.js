import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";

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
                    }
                    decorations {
                        ...decorationContent
                    }
                    promotionRuns(lastPerLevel: true) {
                        id
                        creation {
                            time
                            user
                        }
                        promotionLevel {
                            id
                            name
                            description
                            image
                        }
                    }
                    validations {
                        validationStamp {
                            id
                            name
                            description
                            annotatedDescription
                            image
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
`