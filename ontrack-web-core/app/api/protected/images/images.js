import {backend, getAccessToken} from "@/app/api/protected/backend";
import {NextResponse} from "next/server";

// Next 16 passes the dynamic segments as a promise (#1787)
export const getImage = ({uri}) => async (request, props) => {
    const params = await props.params
    if (params.id) {
        try {
            const dataURL = await fetchImageDataURL(uri(params))
            return NextResponse.json({dataURL})
        } catch (error) {
            return NextResponse.json({error}, {status: 500})
        }
    } else {
        return NextResponse.json({error: "Missing image ID"}, {status: 400})
    }
}

export const putImage = ({uri}) => async (request, props) => {
    const params = await props.params
    if (params.id) {
        const data = await request.text()
        return putImageData(uri(params), data)
    } else {
        return NextResponse.json({error: "Missing image ID"}, {status: 400})
    }
}

const fetchImageDataURL = async (uri) => {
    const accessToken = await getAccessToken()
    if (!accessToken) {
        return {
            data: null,
            response: NextResponse.json({error: "Unauthorized"}, {status: 401})
        }
    }

    const url = `${backend.url}/${uri}`
    const res = await fetch(url, {
        headers: {
            Authorization: `Bearer ${accessToken}`,
        }
    })

    if (res.ok) {
        const arrayBuffer = await res.arrayBuffer()
        const base64String = btoa(
            new Uint8Array(arrayBuffer)
                .reduce((data, byte) => data + String.fromCharCode(byte), '')
        )
        return `data:image/png;base64,${base64String}`
    } else {
        throw new Error(res.statusText)
    }
}

const putImageData = async (uri, data) => {
    const accessToken = await getAccessToken()
    if (!accessToken) {
        return {
            data: null,
            response: NextResponse.json({error: "Unauthorized"}, {status: 401})
        }
    }

    const url = `${backend.url}/${uri}`
    return await fetch(url, {
        method: 'PUT',
        headers: {
            Authorization: `Bearer ${accessToken}`,
        },
        body: data,
    })
}
