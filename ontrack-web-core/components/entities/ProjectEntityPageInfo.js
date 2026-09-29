import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useMemo} from "react";
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

/**
 * For each supported entity type, the field to query and how to turn the entity into the page information.
 */
const entityPageInfos = {
    PROJECT: {
        field: 'project',
        query: gql`
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
        `,
        pageInfo: (project, what) => ({
            entityTypeName: "Project",
            title: projectTitleName(project, what),
            breadcrumbs: [
                ...projectBreadcrumbs(),
                <ProjectLink key="entity" project={project}/>,
            ],
            uri: projectUri(project),
        }),
    },
    BRANCH: {
        field: 'branch',
        query: gql`
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
        `,
        pageInfo: (branch, what) => ({
            entityTypeName: "Branch",
            title: branchTitleName(branch, what),
            breadcrumbs: downToBranchBreadcrumbs({branch}),
            uri: branchUri(branch),
        }),
    },
    PROMOTION_LEVEL: {
        field: 'promotionLevel',
        query: gql`
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
        `,
        pageInfo: (promotionLevel, what) => ({
            entityTypeName: "Promotion level",
            title: promotionLevelTitleName(promotionLevel, what),
            breadcrumbs: [
                ...promotionLevelBreadcrumbs(promotionLevel),
                <PromotionLevelViewTitle
                    key="entity"
                    promotionLevel={promotionLevel}
                    link={true}
                />,
            ],
            uri: promotionLevelUri(promotionLevel),
        }),
    },
    VALIDATION_STAMP: {
        field: 'validationStamp',
        query: gql`
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
        `,
        pageInfo: (validationStamp, what) => ({
            entityTypeName: "Validation stamp",
            title: validationStampTitleName(validationStamp, what),
            breadcrumbs: [
                ...validationStampBreadcrumbs(validationStamp),
                <ValidationStampViewTitle
                    key="entity"
                    validationStamp={validationStamp}
                    link={true}
                />,
            ],
            uri: validationStampUri(validationStamp),
        }),
    },
}

const noPageInfo = {
    entityTypeName: '',
    title: '',
    breadcrumbs: [],
    uri: '',
    entity: {},
}

export const useProjectEntityPageInfo = (type, id, what) => {
    const entityPageInfo = entityPageInfos[type]

    const {data} = useQuery(
        entityPageInfo?.query,
        {
            variables: {id: Number(id)},
            deps: [type, id],
            condition: !!(entityPageInfo && id),
            // The type is kept with the entity: until the entity of a new type is loaded, the
            // previous one must still be read with its own type
            dataFn: data => ({type, entity: data[entityPageInfo.field]}),
        }
    )

    return useMemo(
        () => data?.entity ? {
            ...entityPageInfos[data.type].pageInfo(data.entity, what),
            entity: data.entity,
        } : noPageInfo,
        [data, what]
    )
}
