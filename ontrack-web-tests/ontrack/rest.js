export const restCallPost = async (connection, path, body) => {
    const token = connection.token
    if (!token) {
        throw new Error("No token is available in the connection.")
    }
    return await fetch(
        `${connection.backend}${path}`,
        {
            method: 'POST',
            headers: {
                'X-Ontrack-Token': token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        }
    )
}

export const restCallPut = async (connection, path, body) => {
    const token = connection.token
    if (!token) {
        throw new Error("No token is available in the connection.")
    }
    const response = await fetch(
        `${connection.backend}${path}`,
        {
            method: 'PUT',
            headers: {
                'X-Ontrack-Token': token,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        }
    )
    if (!response.ok) {
        throw new Error(`PUT ${path} failed with status ${response.status}: ${await response.text()}`)
    }
    return response
}

export const restCallPostForJson = async (connection, path, body) => {
    const response = await restCallPost(connection, path, body)
    if (response.status === 202) {
        return null
    } else {
        return response.json()
    }
}
