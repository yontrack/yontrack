import {useEffect, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

const noTemplateRenderers = []

export const useTemplateRenderers = () => {
    const {data} = useQuery(
        gql`
            query TemplatingRenderers {
                templatingRenderers {
                    id
                    name
                }
            }
        `,
        {
            initialData: noTemplateRenderers,
            dataFn: data => data.templatingRenderers,
        }
    )

    return data ?? noTemplateRenderers
}

export default function SelectTemplateRenderer({value, onChange}) {

    const exportFormats = useTemplateRenderers()
    const [exportFormatOptions, setExportFormatOptions] = useState([])
    useEffect(() => {
        setExportFormatOptions(exportFormats.map(format => ({
            value: format.id,
            label: format.name,
        })))
    }, [exportFormats]);

    return (
        <>
            <div data-testid="format">
                <Select
                    value={value}
                    onChange={onChange}
                    options={exportFormatOptions}
                />
            </div>
        </>
    )
}