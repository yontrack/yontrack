import {useEffect, useState} from "react";
import {Button, Dropdown, Space, Typography} from "antd";
import {FaCaretDown, FaEraser, FaFilter, FaLink, FaPencilAlt, FaShare, FaTrashAlt, FaUsers} from "react-icons/fa";
import {gql} from "graphql-request";
import SelectableMenuItem from "@components/common/SelectableMenuItem";
import BuildFilterDialog, {useBuildFilterDialog} from "@components/branches/filters/builds/BuildFilterDialog";
import {isAuthorized} from "@components/common/authorizations";
import {callGraphQL, useQuery} from "@components/services/GraphQL";

export default function BuildFilterDropdown({branch, selectedBuildFilter, onSelectedBuildFilter, onPermalink}) {

    const [items, setItems] = useState([])
    const [forms, setForms] = useState([])
    const [resources, setResources] = useState([])

    const onSelectResourceFilter = (resource) => {
        if (onSelectedBuildFilter) onSelectedBuildFilter(resource)
    }

    const onSelectPredefinedFilter = (form) => {
        onSelectResourceFilter({
            type: form.type,
            name: form.typeName,
            data: {},
            isPredefined: true,
        })
    }

    const buildFilterDialog = useBuildFilterDialog({
        onFilterSuccess: (values, context) => {
            const editedFilterResource = {
                type: context.buildFilterForm.type,
                name: values.data.name,
                data: values.data,
            }
            // Updating the list of resources
            if (editedFilterResource.name) {
                // The filter is named, so either it's present in the list (that's an edition)
                // or it's not and this is an addition
                const exists = resources.find(it => it.name === editedFilterResource.name)
                if (exists) {
                    // Edition
                    setResources(resources.map(it => {
                        if (it.name === editedFilterResource.name) {
                            return editedFilterResource
                        } else {
                            return it
                        }
                    }))
                } else {
                    // Addition
                    setResources([...resources, editedFilterResource])
                }
                // Saving the filter
                callGraphQL({
                    query: gql`
                        mutation SaveBuildFilter(
                            $branchId: Int!,
                            $name: String!,
                            $type: String!,
                            $data: JSON!,
                        ) {
                            saveBuildFilter(input: {
                                branchId: $branchId,
                                name: $name,
                                type: $type,
                                data: $data,
                            }) {
                                errors {
                                    message
                                }
                            }
                        }
                    `,
                    variables: {
                        branchId: Number(branch.id),
                        name: editedFilterResource.name,
                        type: editedFilterResource.type,
                        data: editedFilterResource.data,
                    },
                })
            } else {
                // The filter is not named, this is an anonymous filter, no need to change
                // the list of resources
            }
            // Selecting the new filter
            onSelectedBuildFilter(editedFilterResource)
        }
    })

    const onCreateFilter = (form) => {
        return () => {
            buildFilterDialog.start({branch, buildFilterForm: form})
        }
    }

    const onEditFilter = () => {
        if (selectedBuildFilter) {
            const form = forms.find(form => form.type === selectedBuildFilter.type)
            if (form && !form.isPredefined) {
                buildFilterDialog.start({
                    branch,
                    buildFilterForm: form,
                    buildFilterData: selectedBuildFilter.data,
                    buildFilterName: selectedBuildFilter.name,
                })
            }
        }
    }

    const onShareFilter = (resource) => {
        return () => {
            callGraphQL({
                query: gql`
                    mutation ShareBuildFilter(
                        $branchId: Int!,
                        $name: String!,
                        $type: String!,
                        $data: JSON!,
                    ) {
                        shareBuildFilter(input: {
                            branchId: $branchId,
                            name: $name,
                            data: $data,
                            type: $type,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    branchId: Number(branch.id),
                    name: resource.name,
                    type: resource.type,
                    data: resource.data,
                },
            }).then(() => {
                setResources(resources.map(it => {
                    if (it.name === resource.name) {
                        return {
                            ...it,
                            isShared: true,
                        }
                    } else {
                        return it
                    }
                }))
            })
        }
    }

    const onDeleteFilter = (resource) => {
        return () => {
            // Drop the filter
            onDropFilter()
            // Deleting the filter in the background
            callGraphQL({
                query: gql`
                    mutation DeleteBuildFilter(
                        $branchId: Int!,
                        $name: String!,
                    ) {
                        deleteBuildFilter(input: {
                            branchId: $branchId,
                            name: $name,
                        }) {
                            errors {
                                message
                            }
                        }
                    }
                `,
                variables: {
                    branchId: Number(branch.id),
                    name: resource.name,
                },
            }).then(() => {
                // Removes the entry
                setResources(resources.filter(it => it.name !== resource.name))
            })
        }
    }

    const onPermalinkFilter = () => {
        if (selectedBuildFilter && onPermalink) {
            onPermalink(selectedBuildFilter)
        }
    }

    const onDropFilter = () => {
        onSelectResourceFilter(undefined)
    }

    const buildMenu = () => {

        // Building the menu
        const menu = []

        // Predefined filters
        const predefined = forms.filter(it => it.isPredefined)
        if (predefined.length > 0) {
            menu.push({
                type: 'group',
                key: 'group-predefined',
                label: "Predefined filters:",
                children: predefined.map(form => ({
                    key: `predefined-${form.type}`,
                    label: <SelectableMenuItem
                        text={form.typeName}
                        value={form.typeName === selectedBuildFilter?.name}
                        onChange={() => onSelectPredefinedFilter(form)}
                    />,
                }))
            })
        }

        // Saved filters
        const saved = resources.filter(it => !it.error)
        if (saved.length > 0) {
            menu.push({
                type: 'group',
                key: 'group-saved',
                label: "Saved filters:",
                children: saved.map(resource => ({
                    key: `saved-${resource.name}`,
                    label: <SelectableMenuItem
                        text={
                            <Typography.Text strong={resource.isShared}>{resource.name}</Typography.Text>
                        }
                        value={resource.name === selectedBuildFilter?.name}
                        onChange={() => onSelectResourceFilter(resource)}
                        extra={
                            <>
                                {
                                    resource.isShared ?
                                        <FaUsers
                                            className="ot-command"
                                            title="This filter is shared for all users in this branch"
                                        /> :
                                        (
                                            isAuthorized(branch, "branch", "build_filter_manage") &&
                                            <FaShare
                                                className="ot-command"
                                                title="Share this filter to all users on this branch"
                                                onClick={onShareFilter(resource)}
                                            />
                                        )
                                }
                                {
                                    isAuthorized(branch, "branch", "build_filter_manage") &&
                                    <FaTrashAlt
                                        className="ot-command"
                                        title="Deletes this filter"
                                        onClick={onDeleteFilter(resource)}
                                    />
                                }
                            </>
                        }
                    />,
                }))
            })
        }

        // Current filter
        if (selectedBuildFilter) {

            // Generic actions
            menu.push({
                type: 'divider'
            })

            // Edit the current filter
            if (!selectedBuildFilter.isPredefined) {
                menu.push({
                    key: 'edit',
                    icon: <FaPencilAlt/>,
                    label: "Edit current filter",
                    onClick: onEditFilter,
                })
            }

            // Drop filter
            menu.push({
                key: 'drop',
                icon: <FaEraser/>,
                label: "Clear filter",
                onClick: onDropFilter,
            })

            // Permalink to the filter
            menu.push({
                key: 'permalink',
                icon: <FaLink/>,
                label: "Permalink",
                onClick: onPermalinkFilter,
            })

        }

        // New forms
        const newForms = forms.filter(it => !it.isPredefined)
        if (newForms.length > 0) {
            menu.push({
                type: 'divider'
            })
            menu.push({
                type: 'group',
                key: 'group-new',
                label: "New filters:",
                children: newForms.map(form => ({
                    key: `new-${form.type}`,
                    label: `${form.typeName}...`,
                    onClick: onCreateFilter(form),
                }))
            })
        }

        // OK
        setItems(menu)
    }

    const {data: branchFilters} = useQuery(
        gql`
            query BuildFilters(
                $branchId: Int!,
            ) {
                branches(id: $branchId) {
                    buildFilterForms {
                        type
                        typeName
                        isPredefined
                    }
                    buildFilterResources {
                        type
                        name
                        error
                        isShared
                        data
                    }
                }
            }
        `,
        {
            variables: {branchId: Number(branch?.id)},
            deps: [branch],
            condition: !!branch,
            dataFn: data => data.branches[0],
        }
    )

    // The resources are edited locally afterwards
    useEffect(() => {
        if (branchFilters) {
            setForms(branchFilters.buildFilterForms)
            setResources(branchFilters.buildFilterResources)
        }
    }, [branchFilters]);

    useEffect(() => {
        if (forms || resources) {
            buildMenu()
        }
    }, [selectedBuildFilter, forms, resources]);

    return (
        <>
            <Dropdown
                menu={{
                    items,
                }}
                trigger={['click']}
                className="ot-action"
            >
                <Button>
                    <Space>
                        <FaFilter color={selectedBuildFilter ? 'orange' : undefined}/>
                        <Typography.Text>Build filter</Typography.Text>
                        <FaCaretDown/>
                    </Space>
                </Button>
            </Dropdown>
            <BuildFilterDialog buildFilterDialog={buildFilterDialog}/>
        </>
    )
}