import {useState} from "react";
import {Select} from "antd";
import {gql} from "graphql-request";
import {callGraphQL} from "@components/services/GraphQL";

export default function SelectProject({
                                          id,
                                          value, onChange,
                                          placeholder = "Project name",
                                          idAsValue = false,
                                          multiple = false,
                                          width = '16em',
                                      }) {

    const [projects, setProjects] = useState([])
    const [searching, setSearching] = useState(false)

    const handleChange = (newValue) => {
        if (onChange) onChange(newValue)
    }

    const handleSearch = (token) => {
        if (token && token.length > 2) {
            if (!searching) {
                setSearching(true)
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
                    setProjects(data.projects)
                }).finally(() => {
                    setSearching(false)
                })
            }
        } else {
            setProjects([])
        }
    }

    const handleClear = () => {
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