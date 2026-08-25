import {useQuery} from "@components/services/useQuery";
import {Select} from "antd";
import InlineError from "@components/common/InlineError";

export default function GraphQLEnumSelect({
                                              id,
                                              value,
                                              onChange,
                                              mode,
                                              query,
                                              queryVariables = {},
                                              queryNode,
                                              entryValue,
                                              entryLabel,
                                              width = '12em',
                                          }) {
    const {data: options, loading, error} = useQuery(
        query,
        {
            variables: queryVariables,
            initialData: [],
            dataFn: data => data[queryNode].map(it => ({
                value: entryValue ? entryValue(it) : it,
                label: entryLabel ? entryLabel(it) : it,
            }))
        }
    )

    if (error) {
        return <InlineError message={error}/>
    }

    return (
        <>
            <Select
                id={id}
                value={value}
                onChange={onChange}
                allowClear
                style={{width: width}}
                options={options}
                loading={loading}
                mode={mode}
            />
        </>
    )
}