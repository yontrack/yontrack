import Columns from "@components/common/Columns";
import {useRefData} from "@components/providers/RefDataProvider";
import {useEffect, useState} from "react";
import {Typography} from "antd";
import ValidationGroup from "@components/validationRuns/ValidationGroup";

export default function ValidationGroups({build, onChange}) {

    const {validationRunStatuses} = useRefData()
    const [groupedValidations, setGroupedValidations] = useState([])

    useEffect(() => {
        const statuses = {};
        build.validations.forEach(validation => {
            if (validation.validationRuns.length > 0) {
                const statusID = validation.validationRuns[0].lastStatus.statusID;
                const group = statuses[statusID.id];
                if (!group) {
                    statuses[statusID.id] = {
                        count: 1,
                        validations: [
                            validation
                        ]
                    };
                } else {
                    group.count = group.count + 1;
                    group.validations.push(validation);
                }
            }
        });
        // Sorting
        const localGroupedValidations = [];
        validationRunStatuses.list.forEach(statusID => {
            const group = statuses[statusID.id];
            if (group) {
                localGroupedValidations.push({
                    statusID: statusID,
                    description: `${group.count} validation${group.count > 1 ? 's' : ''} with status ${statusID.name}`,
                    count: group.count,
                    validations: group.validations
                });
            }
        });
        setGroupedValidations(localGroupedValidations)
    }, [build, validationRunStatuses]);


    return (
        <Columns>
            {
                groupedValidations.map(group =>
                    <ValidationGroup key={group.statusID.id} group={group} onChange={onChange}/>
                )
            }
        </Columns>
    )
}