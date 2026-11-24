/*
 * The `THIRD-PARTY-NOTICES.txt` of the UI image (#2182): every package of the SBOM of the UI - its
 * production, non-optional dependencies, which include what Next.js bundles into the browser code -
 * with the license, notice and copying files it packages, verbatim, and an appendix with the
 * standard text of the licenses of the packages which package none, from `notices/licenses/`.
 *
 * Run by the builder stage of the Dockerfile, as
 *
 *     node notices/third-party-notices.js <sbom.json> <node_modules> <licenses dir> <title>
 *
 * The backend image has the same file, in the same format, from
 * `buildSrc/.../notices/ThirdPartyNotices.kt`: change both together. No dependency: it runs before
 * anything but Node is known to be there.
 */
const fs = require('fs')
const path = require('path')

const RULE = '='.repeat(80)
const THIN = '-'.repeat(80)

const NOTICE_FILE = /^(licen[cs]es?|notices?|copying)([^a-z].*)?$/i
const EXPRESSION = /[()]|\s(AND|OR|WITH)\s/
const OPERATORS = new Set(['AND', 'OR', 'WITH'])

const normalize = text => text.replace(/\r\n?/g, '\n').trimEnd() + '\n'

const label = library => `${library.name} ${library.version}`

/**
 * The licenses whose text the declared ones need: the ids of an SPDX expression, or the declared id
 * or name as it is.
 */
function licenseKeys(declared) {
    const keys = declared.flatMap(license =>
        EXPRESSION.test(license)
            ? license.replace(/[()]/g, ' ').split(/\s+/).filter(it => it && !OPERATORS.has(it))
            : [license.trim()]
    )
    return [...new Set(keys)]
}

/** Whether a file at the root of a package is a license, notice or copying file. */
const isNoticeFile = name => NOTICE_FILE.test(name)

/**
 * Renders the notices.
 *
 * @param title First line of the file, naming the image and its version
 * @param libraries `{name, version, licenses, purl, files: [{path, text}]}`
 * @param licenseText Standard text of a license, by SPDX id or name, `null` when there is none
 * @throws Error When a library packages no file and declares no license, or when the text of a
 * license which the appendix needs is missing
 */
function render(title, libraries, licenseText) {
    const sorted = [...libraries].sort((a, b) =>
        a.name < b.name ? -1 : a.name > b.name ? 1 : a.version < b.version ? -1 : a.version > b.version ? 1 : 0
    )

    const undeclared = sorted.filter(it => it.files.length === 0 && it.licenses.length === 0)
    if (undeclared.length > 0) {
        throw new Error(`These libraries package no license file and declare no license: ${undeclared.map(label).join(', ')}`)
    }

    // License key -> the libraries needing its text from the appendix
    const appendix = new Map()
    sorted.filter(it => it.files.length === 0).forEach(library => {
        licenseKeys(library.licenses).forEach(key => {
            if (!appendix.has(key)) appendix.set(key, [])
            appendix.get(key).push(library)
        })
    })
    const keys = [...appendix.keys()].sort()
    const texts = new Map(keys.map(key => [key, licenseText(key)]))
    const missing = keys.filter(key => texts.get(key) == null)
    if (missing.length > 0) {
        throw new Error(
            'No text for the license(s) declared by libraries which package no license file of their own: ' +
            missing.map(key => `${key} (${appendix.get(key).map(label).join(', ')})`).join('; ')
        )
    }

    // Normalized text -> where it was given first
    const given = new Map()

    let out = `${title}\n\n`
    out += 'This file lists the third-party software shipped in this image, each library with the\n' +
        'licenses it declares and, verbatim, the license, notice and copying files it packages.\n' +
        'The text of the licenses of the libraries which package no such file is in the\n' +
        'appendix at the end of this file.\n\n'
    sorted.forEach(library => {
        out += `${RULE}\n${label(library)}\nLicense: ${library.licenses.join(', ')}\n`
        if (library.purl) out += `Package: ${library.purl}\n`
        if (library.files.length === 0) {
            out += '\nThis library packages no license file of its own: the text of its license is in the appendix.\n'
        } else {
            library.files.forEach(file => {
                const text = normalize(file.text)
                const first = given.get(text)
                if (first) {
                    out += `\n${file.path}: same text as ${first}, above\n`
                } else {
                    given.set(text, `${file.path} of ${label(library)}`)
                    out += `\n${file.path}\n${THIN}\n${text}`
                }
            })
        }
        out += '\n'
    })
    if (keys.length > 0) {
        out += `${RULE}\nAPPENDIX - LICENSE TEXTS\n${RULE}\n\n`
        keys.forEach(key => {
            out += `${key}\n${THIN}\n${normalize(texts.get(key))}\n`
        })
    }
    return out
}

/** Every installed package under a `node_modules`, by `name@version`, nested ones included. */
function indexPackages(nodeModules) {
    const index = new Map()
    const visit = dir => {
        if (!fs.existsSync(dir)) return
        fs.readdirSync(dir, {withFileTypes: true}).forEach(entry => {
            if (!entry.isDirectory() || entry.name.startsWith('.')) return
            const full = path.join(dir, entry.name)
            if (entry.name.startsWith('@')) {
                visit(full)
                return
            }
            const manifest = path.join(full, 'package.json')
            if (fs.existsSync(manifest)) {
                const {name, version} = JSON.parse(fs.readFileSync(manifest, 'utf8'))
                const key = `${name}@${version}`
                if (name && version && !index.has(key)) index.set(key, full)
            }
            visit(path.join(full, 'node_modules'))
        })
    }
    visit(nodeModules)
    return index
}

const declaredLicenses = component =>
    (component.licenses || [])
        .map(entry => entry.license?.id ?? entry.license?.name ?? entry.expression)
        .filter(Boolean)

/**
 * The libraries of a CycloneDX SBOM of npm packages, with the notice files they package, read from
 * where they are installed.
 *
 * @throws Error When a package of the SBOM is not installed
 */
function collect(sbom, nodeModules) {
    const index = indexPackages(nodeModules)
    return (sbom.components || []).map(component => {
        const name = component.group ? `${component.group}/${component.name}` : component.name
        const dir = index.get(`${name}@${component.version}`)
        if (!dir) throw new Error(`${name}@${component.version} is in the SBOM but not installed under ${nodeModules}`)
        const files = fs.readdirSync(dir, {withFileTypes: true})
            .filter(entry => entry.isFile() && isNoticeFile(entry.name))
            .map(entry => entry.name)
            .sort()
            .map(file => ({path: file, text: fs.readFileSync(path.join(dir, file), 'utf8').replace(/\r\n?/g, '\n')}))
        return {name, version: component.version, licenses: declaredLicenses(component), purl: component.purl ?? null, files}
    })
}

function main([sbomFile, nodeModules, licensesDir, title]) {
    if (!title) {
        console.error('Usage: node third-party-notices.js <sbom.json> <node_modules> <licenses dir> <title>')
        process.exit(2)
    }
    const sbom = JSON.parse(fs.readFileSync(sbomFile, 'utf8'))
    const libraries = collect(sbom, nodeModules)
    const licenseText = key => {
        const file = path.join(licensesDir, `${key}.txt`)
        return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null
    }
    try {
        process.stdout.write(render(title, libraries, licenseText))
    } catch (e) {
        console.error(`${e.message} - license texts are read from ${licensesDir}`)
        process.exit(1)
    }
}

module.exports = {render, licenseKeys, isNoticeFile, collect}

if (require.main === module) {
    main(process.argv.slice(2))
}
