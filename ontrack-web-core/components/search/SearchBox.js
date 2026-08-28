import {Dropdown, Input} from "antd";
import {useMemo, useState} from "react";
import {FaSearch} from "react-icons/fa";
import SearchBoxResults from "@components/search/SearchBoxResults";
import debounce from "lodash.debounce";

export default function SearchBox({style}) {
    const [searchValue, setSearchValue] = useState('');
    const [query, setQuery] = useState('');
    const [dropdownOpen, setDropdownOpen] = useState(false);

    const debouncedSearch = useMemo(
        () => debounce((value) => {
            setQuery(value);
        }, 500),
        []
    );

    const handleChange = (e) => {
        const value = e.target.value;
        setSearchValue(value);
        if (value.length >= 3) {
            debouncedSearch(value);
            setDropdownOpen(true);
        } else {
            debouncedSearch.cancel();
            setQuery('');
            setDropdownOpen(false);
        }
    };

    const handleSearch = (value) => {
        if (value && value.length >= 3) {
            debouncedSearch.cancel();
            setQuery(value);
            setDropdownOpen(true);
        }
    };

    const closeDropdown = () => {
        setDropdownOpen(false);
    };

    return (
        <>
            <Dropdown
                open={dropdownOpen}
                onOpenChange={(open) => {
                    if (!open) setDropdownOpen(false);
                    else if (searchValue.length >= 3) setDropdownOpen(true);
                }}
                trigger={['click']}
                placement="bottomRight"
                dropdownRender={() => (
                    <div style={{
                        backgroundColor: 'var(--ot-bg-elevated)',
                        boxShadow: '0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05)',
                        borderRadius: '8px',
                        maxHeight: '500px',
                        overflow: 'auto',
                        minWidth: '400px',
                    }}>
                        <SearchBoxResults query={query} onSelect={closeDropdown}/>
                    </div>
                )}
            >
                <Input.Search
                    style={style}
                    placeholder="Search..."
                    value={searchValue}
                    onChange={handleChange}
                    onSearch={handleSearch}
                    allowClear={true}
                    prefix={<FaSearch/>}
                />
            </Dropdown>
        </>
    );
}