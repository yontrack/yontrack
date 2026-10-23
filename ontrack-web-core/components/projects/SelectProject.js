import {useEffect, useMemo, useRef, useState} from "react";
import {Select} from "antd";
import {gql} from "graphql-request";
import debounce from "lodash.debounce";
import {callGraphQL} from "@components/services/GraphQL";

export default function SelectProject({
                                          id,
                                          value, onChange,
                                          placeholder = "Project name",
                                          idAsValue = false,
                                          multiple = false,
                                          width = '16em',
                                          debounceTimeout = 300,
                                      }) {

    const [projects, setProjects] = useState([])
    // Latest token typed, so that a response for an earlier token never overwrites a later one
    const latestToken = useRef('')

    const handleChange = (newValue) => {
        if (onChange) onChange(newValue)
    }

    const searchProjects = useMemo(() => debounce((token) => {
        callGraphQL({
            query: gql`
                query SearchProjects($token: String!) {
                    projects(pattern: $token) {
                        id
                        name
                    }
                }
            `,
            variables: {token},
        }).then(data => {
            if (token === latestToken.current) {
                setProjects(data.projects)
            }
        })
    }, debounceTimeout), [debounceTimeout])

    useEffect(() => () => searchProjects.cancel(), [searchProjects])

    const handleSearch = (token) => {
        latestToken.current = token
        if (token && token.length > 2) {
            searchProjects(token)
        } else {
            searchProjects.cancel()
            setProjects([])
        }
    }

    const handleClear = () => {
        latestToken.current = ''
        searchProjects.cancel()
        setProjects([])
        if (onChange) onChange(null)
    }

    return (
        <>
            <Select
                id={id}
                data-testid={id}
                showSearch={true}
                value={value}
                placeholder={placeholder}
                defaultActiveFirstOption={true}
                suffixIcon={null}
                filterOption={false}
                onSearch={handleSearch}
                onChange={handleChange}
                allowClear={true}
                onClear={handleClear}
                notFoundContent={null}
                options={(projects || []).map((d) => ({
                    value: idAsValue ? Number(d.id) : d.name,
                    label: d.name,
                }))}
                style={{
                    width: width,
                }}
                mode={multiple ? "multiple" : undefined}
            />
        </>
    )
}