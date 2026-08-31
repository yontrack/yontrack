import {createContext, useCallback, useEffect, useState} from "react";
import {gql} from "graphql-request";
import {
    gqlValidationStampFilterFragment
} from "@components/branches/filters/validationStamps/ValidationStampFilterGraphQLFragments";
import {getLocallySelectedValidationFilter, setLocallySelectedValidationStampFilter} from "@components/storage/local";
import NewValidationStampFilterDialog, {
    useNewValidationStampFilterDialog
} from "@components/branches/filters/validationStamps/NewValidationStampFilterDialog";
import EditValidationStampFilterDialog, {
    useEditValidationStampFilterDialog
} from "@components/branches/filters/validationStamps/EditValidationStampFilterDialog";
import {usePreferences} from "@components/providers/PreferencesProvider";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";

const gqlUpdateValidationStampFilter = gql`
    mutation UpdateValidationStampFilter(
        $id: Int!,
        $vsNames: [String!]!,
    ) {
        updateValidationStampFilter(input: {
            id: $id,
            vsNames: $vsNames,
        }) {
            errors {
                message
            }
        }
    }
`

// noinspection JSUnusedLocalSymbols
export const ValidationStampFilterContext = createContext({
    // List of available filters
    filters: [],
    // Selected filter
    selectedFilter: undefined,
    // Selects a filter
    selectFilter: (filter) => {
    },
    // Toggles a validation stamp for the current filter
    toggleValidationStamp: (validationStamp) => {
    },
    toggleAll: () => {
    },
    toggleNone: () => {
    },
    // Inline edition management
    inlineEdition: false,
    startInlineEdition: (filter) => {
    },
    stopInlineEdition: () => {
    },
    // Dialogs
    newFilter: () => {
    },
    editFilter: (filter) => {
    },
    deleteFilter: (filter) => {
    },
    // Grouping options
    grouping: false,
    setGrouping: (value) => {
    },
})

export default function ValidationStampFilterContextProvider({branch, children}) {

    const [refreshCount, refresh] = useRefresh()

    const {data} = useQuery(
        gql`
            query GetValidationStampFilters(
                $branchId: Int!,
            ) {
                branch(id: $branchId) {
                    validationStampFilters(all: true) {
                        ...validationStampFilterContent
                    }
                    validationStamps {
                        name
                    }
                }
            }

            ${gqlValidationStampFilterFragment}
        `,
        {
            variables: {
                branchId: Number(branch?.id),
            },
            deps: [branch, refreshCount],
            condition: !!branch,
            initialData: null,
            dataFn: data => data.branch,
        }
    )

    // Validation stamp names
    const validationStampNames = data?.validationStamps?.map(it => it.name) ?? []

    // The filters are seeded by the query, then edited in place by the dialogs below
    const [filters, setFilters] = useState([])
    const [selectedFilter, setSelectedFilter] = useState()

    useEffect(() => {
        if (data) {
            setFilters(data.validationStampFilters)
            // Selection of the initial filter
            // TODO Checks also the permalink
            const initialFilter = getLocallySelectedValidationFilter(branch.id)
            if (initialFilter) {
                setSelectedFilter(initialFilter)
            }
        }
    }, [data])

    const selectFilter = (filter) => {
        // TODO exitInlineEdit() // Stops any current edition
        setLocallySelectedValidationStampFilter(branch.id, filter)
        setSelectedFilter(filter)
    }

    const updateFilter = (vsNames) => {
        callGraphQL({
            query: gqlUpdateValidationStampFilter,
            variables: {
                id: selectedFilter.id,
                vsNames,
            },
        }).then(() => {
            setSelectedFilter({
                ...selectedFilter,
                vsNames,
            })
        })
    }

    const toggleValidationStamp = useCallback((validationStamp) => {
        if (selectedFilter) {
            let vsNames = selectedFilter.vsNames
            if (vsNames.indexOf(validationStamp.name) >= 0) {
                vsNames = vsNames.filter(it => it !== validationStamp.name).sort()
            } else {
                vsNames = [...vsNames, validationStamp.name].sort()
            }
            updateFilter(vsNames)
        }
    }, [selectedFilter])

    const toggleAll = useCallback(() => {
        if (selectedFilter) {
            updateFilter(validationStampNames)
        }
    }, [selectedFilter])

    const toggleNone = useCallback(() => {
        if (selectedFilter) {
            updateFilter([])
        }
    }, [selectedFilter])

    const [inlineEdition, setInlineEdition] = useState(false)

    const startInlineEdition = (filter) => {
        selectFilter(filter)
        setInlineEdition(true)
    }

    const stopInlineEdition = () => {
        setInlineEdition(false)
    }

    const newValidationStampFilterDialog = useNewValidationStampFilterDialog({
        onSuccess: (values) => {
            // Creates the filter
            callGraphQL({
                query: gql`
                    mutation CreateValidationStampFilter(
                        $name: String!
                    ) {
                        createValidationStampFilter(input: {
                            name: $name
                        }) {
                            errors {
                                message
                            }
                            validationStampFilter {
                                ...validationStampFilterContent
                            }
                        }
                    }

                    ${gqlValidationStampFilterFragment}
                `,
                variables: values,
            }).then(data => {
                setFilters([
                    ...filters,
                    data.createValidationStampFilter.validationStampFilter,
                ].sort())
                // TODO Enters in edition mode immediately
            })
        }
    })

    const newFilter = () => {
        newValidationStampFilterDialog.start()
    }

    const editValidationStampFilterDialog = useEditValidationStampFilterDialog({
        onSuccess: (values, {filter}) => {
            callGraphQL({
                query: gqlUpdateValidationStampFilter,
                variables: {
                    id: filter.id,
                    vsNames: values.vsNames,
                },
            }).then(() => {
                const updatedFilter = {
                    ...filter,
                    vsNames: values.vsNames,
                }
                setFilters(filters.map(it => {
                    if (it.id === filter.id) {
                        return updatedFilter
                    } else {
                        return it
                    }
                }))
                selectFilter(updatedFilter)
            })
        }
    })

    const editFilter = (filter) => {
        editValidationStampFilterDialog.start({filter})
    }

    const deleteFilter = async (filter) => {
        callGraphQL({
            query: gql`
                mutation DeleteValidationStampFilter($id: Int!) {
                    deleteValidationStampFilterById(input: {
                        id: $id,
                    }) {
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {
                id: Number(filter.id),
            },
        }).then(refresh)
    }

    // Preferences
    const {branchViewVsGroups, setPreferences} = usePreferences()

    // Grouping
    const [grouping, setGrouping] = useState(branchViewVsGroups)
    const onGrouping = (value) => {
        setPreferences({branchViewVsGroups: value})
        setGrouping(value)
    }

    const context = {
        filters,
        selectedFilter,
        selectFilter,
        toggleValidationStamp,
        toggleAll,
        toggleNone,
        inlineEdition,
        startInlineEdition,
        stopInlineEdition,
        newFilter,
        editFilter,
        deleteFilter,
        grouping,
        setGrouping: onGrouping,
    }

    return (
        <ValidationStampFilterContext.Provider value={context}>
            <>
                {children}
                <NewValidationStampFilterDialog newValidationStampFilterDialog={newValidationStampFilterDialog}/>
                <EditValidationStampFilterDialog branch={branch}
                                                 editValidationStampFilterDialog={editValidationStampFilterDialog}/>
            </>
        </ValidationStampFilterContext.Provider>
    )
}
