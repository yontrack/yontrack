/**
 * @jest-environment node
 */
const fs = require('fs')
const os = require('os')
const path = require('path')
const {render, licenseKeys, isNoticeFile, collect} = require('../../notices/third-party-notices')

const THIN = '-'.repeat(80)

const texts = {MIT: 'The MIT license text', ISC: 'The ISC license text'}

const lib = {
    name: 'some-lib',
    version: '1.2.3',
    licenses: ['MIT'],
    purl: 'pkg:npm/some-lib@1.2.3',
    files: [
        {path: 'LICENSE', text: 'Copyright 2026 Example\nMIT stuff'},
        {path: 'NOTICE.md', text: 'This product includes Example.'},
    ],
}

const renderAll = (...libraries) => render('Yontrack 6.0.0 - UI image', libraries, key => texts[key] ?? null)

describe('third-party notices of the UI image', () => {

    test('an entry gives the name, version, licenses, package and every packaged file verbatim', () => {
        const notices = renderAll(lib)
        expect(notices.startsWith('Yontrack 6.0.0 - UI image\n')).toBe(true)
        expect(notices).toContain('some-lib 1.2.3\nLicense: MIT\nPackage: pkg:npm/some-lib@1.2.3\n')
        expect(notices).toContain(`LICENSE\n${THIN}\nCopyright 2026 Example\nMIT stuff\n`)
        expect(notices).toContain('This product includes Example.')
    })

    test('the libraries are in name order', () => {
        const notices = renderAll({...lib, name: 'zzz'}, {...lib, name: '@aaa/first'})
        expect(notices.indexOf('@aaa/first 1.2.3')).toBeLessThan(notices.indexOf('zzz 1.2.3'))
    })

    test('the appendix has the text of the licenses of the libraries packaging no file of their own', () => {
        const bare = {...lib, name: 'bare', licenses: ['MIT AND ISC'], files: []}
        const notices = renderAll(lib, bare)
        const appendix = notices.substring(notices.indexOf('APPENDIX'))
        expect(notices).toContain('packages no license file of its own: the text of its license is in the appendix')
        expect(appendix).toContain(`ISC\n${THIN}\nThe ISC license text\n`)
        expect(appendix).toContain(`MIT\n${THIN}\nThe MIT license text\n`)
    })

    test('no appendix when every library packages its own files', () => {
        expect(renderAll(lib)).not.toContain('APPENDIX')
    })

    test('a missing license text fails, naming the license and the library', () => {
        const bare = {...lib, name: 'bare', licenses: ['EPL-2.0'], files: []}
        expect(() => renderAll(bare)).toThrow(/EPL-2\.0 \(bare 1\.2\.3\)/)
    })

    test('a library with neither a file nor a declared license fails', () => {
        expect(() => renderAll({...lib, licenses: [], files: []})).toThrow(/some-lib 1\.2\.3/)
    })

    test('a file identical to one already given refers to it', () => {
        const other = {
            ...lib,
            name: 'zzz-other',
            files: [{path: 'LICENSE.txt', text: 'Copyright 2026 Example\r\nMIT stuff\n'}],
        }
        const notices = renderAll(lib, other)
        expect(notices.match(/MIT stuff/g)).toHaveLength(1)
        expect(notices).toContain('LICENSE.txt: same text as LICENSE of some-lib 1.2.3, above')
    })

    test('license keys of ids, names and expressions', () => {
        expect(licenseKeys(['MIT'])).toEqual(['MIT'])
        expect(licenseKeys(['(MPL-2.0 OR Apache-2.0)'])).toEqual(['MPL-2.0', 'Apache-2.0'])
        expect(licenseKeys(['MIT AND ISC'])).toEqual(['MIT', 'ISC'])
        expect(licenseKeys(['GPL-2.0-only WITH Classpath-exception-2.0'])).toEqual(['GPL-2.0-only', 'Classpath-exception-2.0'])
    })

    test('notice files of a package', () => {
        ['LICENSE', 'LICENSE.md', 'license', 'LICENCE-MIT', 'NOTICE', 'COPYING.txt', 'LICENSE-APACHE']
            .forEach(name => expect(isNoticeFile(name)).toBe(true))
        ;['licensed.js', 'README.md', 'package.json', 'index.js']
            .forEach(name => expect(isNoticeFile(name)).toBe(false))
    })

    test('collecting the libraries of an SBOM from node_modules', () => {
        const root = fs.mkdtempSync(path.join(os.tmpdir(), 'notices-'))
        try {
            const write = (file, content) => {
                fs.mkdirSync(path.dirname(path.join(root, file)), {recursive: true})
                fs.writeFileSync(path.join(root, file), content)
            }
            write('node_modules/a/package.json', JSON.stringify({name: 'a', version: '1.0.0'}))
            write('node_modules/a/LICENSE', 'A license')
            write('node_modules/@s/b/package.json', JSON.stringify({name: '@s/b', version: '2.0.0'}))
            // A second version of `a`, nested
            write('node_modules/@s/b/node_modules/a/package.json', JSON.stringify({name: 'a', version: '0.9.0'}))
            write('node_modules/@s/b/node_modules/a/NOTICE', 'Old a notice')
            const sbom = {
                components: [
                    {name: 'a', version: '1.0.0', purl: 'pkg:npm/a@1.0.0', licenses: [{license: {id: 'MIT'}}]},
                    {group: '@s', name: 'b', version: '2.0.0', licenses: [{expression: 'MIT OR ISC'}]},
                    {name: 'a', version: '0.9.0', licenses: [{license: {name: 'Custom'}}]},
                ],
            }
            expect(collect(sbom, path.join(root, 'node_modules'))).toEqual([
                {name: 'a', version: '1.0.0', licenses: ['MIT'], purl: 'pkg:npm/a@1.0.0', files: [{path: 'LICENSE', text: 'A license'}]},
                {name: '@s/b', version: '2.0.0', licenses: ['MIT OR ISC'], purl: null, files: []},
                {name: 'a', version: '0.9.0', licenses: ['Custom'], purl: null, files: [{path: 'NOTICE', text: 'Old a notice'}]},
            ])
            expect(() => collect({components: [{name: 'missing', version: '1'}]}, path.join(root, 'node_modules')))
                .toThrow(/missing@1/)
        } finally {
            fs.rmSync(root, {recursive: true, force: true})
        }
    })

    test('the license texts kept for the UI image are SPDX\'s', () => {
        const mit = fs.readFileSync(path.join(__dirname, '../../notices/licenses/MIT.txt'), 'utf8')
        expect(mit.startsWith('MIT License')).toBe(true)
    })
})
