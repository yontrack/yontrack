import {Button, Dropdown, Space, Typography} from "antd";
import {FaCaretDown, FaCheck, FaLink} from "react-icons/fa";
import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";
import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {StandardBuildFilterFields} from "@components/framework/build-filter/net.nemerosa.ontrack.service.StandardBuildFilterProvider";
import {
    boundaryFilterLabel,
    sanitizeBoundaryFilter,
    savedBoundaryFilters,
} from "@components/extension/findings/findingsModel";

/** The branches of the exposures, once each. */
const branchesOf = (exposures) => {
    const branches = new Map()
    exposures.forEach(({branch}) => branches.set(branch.id, branch))
    return [...branches.values()]
}

const sameFilter = (a, b) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

/**
 * Picks the one build filter applied, by name, to every lane of the exposure of a finding. It is a
 * way of reading the page: a saved filter has its criteria copied, and nothing is saved from here.
 *
 * @param findingId ID of the finding
 * @param exposures The exposures of the finding, whose branches are the ones the filter applies to
 * @param filter Current filter, `{name, data}`, `undefined` for none
 * @param onFilter Called with the filter chosen, `undefined` for none
 * @param onPermalink Called to get a link to the current filter
 */
export default function FindingBoundaryFilter({findingId, exposures, filter, onFilter, onPermalink}) {

    const branches = branchesOf(exposures)

    const {data: saved} = useQuery(
        gql`
            query FindingBoundarySavedFilters($id: Int!) {
                finding(id: $id) {
                    exposures {
                        branch {
                            id
                            name
                            buildFilterResources {
                                type
                                name
                                error
                                data
                            }
                        }
                    }
                }
            }
        `,
        {
            variables: {id: findingId},
            deps: [findingId],
            dataFn: data => savedBoundaryFilters(branchesOf(data.finding?.exposures ?? [])),
        }
    )

    const dialog = useFormDialog({
        init: (form) => form.resetFields(),
        onSuccess: (values) => onFilter(sanitizeBoundaryFilter({data: values.data})),
    })

    const selectedIcon = (selected) => selected ? <FaCheck/> : <span style={{display: 'inline-block', width: 14}}/>

    const items = [
        {
            key: 'none',
            icon: selectedIcon(!filter),
            label: boundaryFilterLabel(undefined),
            onClick: () => onFilter(undefined),
        },
        {
            key: 'new',
            icon: selectedIcon(false),
            label: "New filter",
            onClick: () => dialog.start({}),
        },
    ]
    if (saved?.length > 0) {
        items.push({
            type: 'group',
            key: 'saved',
            label: "Saved on these branches",
            children: saved.map(it => ({
                key: it.key,
                icon: selectedIcon(sameFilter(it.filter, filter)),
                disabled: !it.filter,
                label: <Space size={4}>
                    {it.name}
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        · {it.branches.join(', ')}{it.disabledReason ? ` · ${it.disabledReason}` : ''}
                    </Typography.Text>
                </Space>,
                onClick: () => onFilter(it.filter),
            })),
        })
    }
    if (filter) {
        items.push({type: 'divider'}, {
            key: 'permalink',
            icon: <FaLink/>,
            label: "Permalink",
            onClick: onPermalink,
        })
    }

    return (
        <>
            <Space size={8} wrap>
                <Typography.Text type="secondary" style={{fontSize: 13}}>A fix counts once a build matches</Typography.Text>
                <Dropdown menu={{items}} trigger={['click']}>
                    <Button size="small" data-testid="finding-boundary-filter">
                        <Space size={4}>
                            {boundaryFilterLabel(filter)}
                            <FaCaretDown/>
                        </Space>
                    </Button>
                </Dropdown>
            </Space>
            <FormDialog id="finding-boundary-filter-form" dialog={dialog} width={720}
                        header={<Typography.Title level={4}>A fix counts once a build matches</Typography.Title>}>
                {
                    dialog.open &&
                    <StandardBuildFilterFields branches={branches} singleBuild={true}/>
                }
            </FormDialog>
        </>
    )
}
