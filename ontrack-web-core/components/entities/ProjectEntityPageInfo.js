import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {useEffect, useState} from "react";
import {
    branchTitleName,
    projectTitleName,
    promotionLevelTitleName,
    validationStampTitleName
} from "@components/common/Titles";
import {
    downToBranchBreadcrumbs,
    projectBreadcrumbs,
    promotionLevelBreadcrumbs,
    validationStampBreadcrumbs
} from "@components/common/Breadcrumbs";
import PromotionLevelViewTitle from "@components/promotionLevels/PromotionLevelViewTitle";
import {
    branchUri,
    buildUri,
    projectUri,
    promotionLevelUri,
    promotionRunUri,
    validationStampUri
} from "@components/common/Links";
import ProjectLink from "@components/projects/ProjectLink";
import ValidationStampViewTitle from "@components/validationStamps/ValidationStampViewTitle";
import BranchLink from "@components/branches/BranchLink";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import BuildLink from "@components/builds/BuildLink";
import PromotionRunLink from "@components/promotionRuns/PromotionRunLink";

export const extractProjectEntityInfo = (type, entity) => {
    switch (type) {
        case 'PROJECT': {
            return {
                type: 'Project',
                name: entity.name,
                compositeName: entity.name,
                href: projectUri(entity),
                component: <ProjectLink project={entity}/>,
            }
        }
        case 'BRANCH': {
            return {
                type: 'Branch',
                name: entity.name,
                compositeName: `${entity.project.name}/${entity.name}`,
                href: branchUri(entity),
                component: <BranchLink branch={entity}/>,
            }
        }
        case 'PROMOTION_LEVEL': {
            return {
                type: 'Promotion level',
                name: entity.name,
                compositeName: `${entity.branch.project.name}/${entity.branch.name}/${entity.name}`,
                href: promotionLevelUri(entity),
                component: <PromotionLevelLink promotionLevel={entity}/>,
            }
        }
        case 'VALIDATION_STAMP': {
            return {
                type: 'Validation stamp',
                name: entity.name,
                compositeName: `${entity.branch.project.name}/${entity.branch.name}/${entity.name}`,
                href: validationStampUri(entity),
                component: <ValidationStampLink validationStamp={entity}/>,
            }
        }
        case 'BUILD': {
            return {
                type: 'Build',
                name: entity.name,
                compositeName: `${entity.branch.project.name}/${entity.branch.name}/${entity.name}`,
                href: buildUri(entity),
                component: <BuildLink build={entity}/>,
            }
        }
        case 'VALIDATION_RUN': {
            // TODO
            break
        }
        case 'PROMOTION_RUN': {
            return {
                type: 'Promotion run',
                name: `${entity.build.name} x ${entity.promotionLevel.name}`,
                compositeName: `${entity.build.branch.project.name}/${entity.build.branch.name}/${entity.promotionLevel.name}/${entity.build.name}`,
                href: promotionRunUri(entity),
                component: <PromotionRunLink promotionRun={entity}/>,
            }
        }
    }
}

export const useProjectEntityPageInfo = (type, id, what) => {
    const client = useGraphQLClient()
    const [title, setTitle] = useState('')
    const [breadcrumbs, setBreadcrumbs] = useState([])
    const [uri, setUri] = useState('')
    const [entity, setEntity] = useState({})
    const [entityTypeName, setEntityTypeName] = useState('')

    useEffect(() => {
        if (client && type && id) {
            switch (type) {
                case 'PROJECT': {
                    client.request(
                        gql`
                            query EntityInformation( $id: Int!, ) {
                                project(id: $id) {
                                    id
                                    name
                                    authorizations {
                                        name
                                        action
                                        authorized
                                    }
                                }
                            }
                        `, {id: Number(id)}
                    ).then(data => {
                        setEntityTypeName("Project")
                        setTitle(projectTitleName(data.project, what))

                        const breadcrumbs = projectBreadcrumbs()
                        breadcrumbs.push(
                            <ProjectLink project={data.project}/>
                        )
                        setBreadcrumbs(breadcrumbs)

                        setUri(projectUri(data.project))

                        setEntity(data.project)
                    })
                    break
                }
                case 'BRANCH': {
                    client.request(
                        gql`
                            query EntityInformation( $id: Int!, ) {
                                branch(id: $id) {
                                    id
                                    name
                                    project {
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
                        `, {id: Number(id)}
                    ).then(data => {
                        setEntityTypeName("Branch")
                        setTitle(branchTitleName(data.branch, what))
                        setBreadcrumbs(downToBranchBreadcrumbs(data))
                        setUri(branchUri(data.branch))
                        setEntity(data.branch)
                    })
                    break
                }
                case 'PROMOTION_LEVEL': {
                    client.request(
                        gql`
                            query EntityInformation( $id: Int!, ) {
                                promotionLevel(id: $id) {
                                    id
                                    name
                                    image
                                    branch {
                                        id
                                        name
                                        displayName
                                        project {
                                            id
                                            name
                                        }
                                    }
                                    authorizations {
                                        name
                                        action
                                        authorized
                                    }
                                }
                            }
                        `, {id: Number(id)}
                    ).then(data => {
                        setEntityTypeName("Promotion level")
                        setTitle(promotionLevelTitleName(data.promotionLevel, what))

                        const breadcrumbs = promotionLevelBreadcrumbs(data.promotionLevel)
                        breadcrumbs.push(
                            <PromotionLevelViewTitle
                                key="entity"
                                promotionLevel={data.promotionLevel}
                                link={true}
                            />
                        )
                        setBreadcrumbs(breadcrumbs)

                        setUri(promotionLevelUri(data.promotionLevel))

                        setEntity(data.promotionLevel)
                    })
                    break
                }
                case 'VALIDATION_STAMP': {
                    client.request(
                        gql`
                            query EntityInformation( $id: Int!, ) {
                                validationStamp(id: $id) {
                                    id
                                    name
                                    image
                                    branch {
                                        id
                                        name
                                        displayName
                                        project {
                                            id
                                            name
                                        }
                                    }
                                    authorizations {
                                        name
                                        action
                                        authorized
                                    }
                                }
                            }
                        `, {id: Number(id)}
                    ).then(data => {
                        setEntityTypeName("Validation stamp")
                        setTitle(validationStampTitleName(data.validationStamp, what))

                        const breadcrumbs = validationStampBreadcrumbs(data.validationStamp)
                        breadcrumbs.push(
                            <ValidationStampViewTitle
                                key="entity"
                                validationStamp={data.validationStamp}
                                link={true}
                            />
                        )
                        setBreadcrumbs(breadcrumbs)

                        setUri(validationStampUri(data.validationStamp))

                        setEntity(data.validationStamp)
                    })
                    break
                }
            }
        }
    }, [client, type, id]);

    return {
        entityTypeName,
        title,
        breadcrumbs,
        uri,
        entity,
    }
}