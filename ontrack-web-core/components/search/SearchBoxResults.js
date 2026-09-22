import {useRefData} from "@components/providers/RefDataProvider";
import SearchBoxTypeResults from "@components/search/SearchBoxTypeResults";
import {Empty, Space} from "antd";
import React, {useEffect, useState} from "react";

export default function SearchBoxResults({query, onSelect}) {
    const {searchResultTypes} = useRefData();

    const [results, setResults] = useState({});

    useEffect(() => {
        setResults({});
    }, [query]);

    if (searchResultTypes.length === 0 || !query || query.length < 3) {
        return null;
    }

    const onResults = (typeId, count) => {
        setResults(prev => ({
            ...prev,
            [typeId]: count
        }));
    };

    const totalResults = Object.values(results).reduce((acc, count) => acc + count, 0);
    const allFinished = searchResultTypes.length > 0 && Object.keys(results).length === searchResultTypes.length;

    return (
        <Space orientation="vertical" style={{width: '100%'}}>
            {
                searchResultTypes.map(type => (
                    <SearchBoxTypeResults
                        key={type.id}
                        type={type}
                        query={query}
                        onSelect={onSelect}
                        onResults={onResults}
                    />
                ))
            }
            {
                allFinished && totalResults === 0 && (
                    <div style={{padding: '16px'}}>
                        <Empty description="No result found" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
                    </div>
                )
            }
        </Space>
    )
}
