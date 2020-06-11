'use strict'

// copied from Antora

const { posix: path } = require('path')

/**
 * Computes the shortest relative path between two URLs.
 *
 * This function takes into account directory index URLs and extensionless
 * URLs. It assumes it's working with root-relative URLs, not qualified URLs
 * with potentially different hosts.
 *
 * @memberof asciidoc-loader
 *
 * @param {String} from - The root-relative start URL.
 * @param {String} to - The root-relative target URL.
 * @param {String} [hash=''] - The URL hash to append to the URL (not #).
 *
 * @returns {String} The shortest relative path to travel from the start URL to the target URL.
 */
function computeRelativeUrlPath(from, to, hash = '') {
    if (to.charAt() === '/') {
        return to === from
            ? hash || (isDir(to) ? './' : path.basename(to))
            : (path.relative(path.dirname(from + '.'), to) || '.') + (isDir(to) ? '/' + hash : hash)
    } else {
        return to + hash
    }
}

function isDir(str) {
    return str.charAt(str.length - 1) === '/'
}

// with the above, we can implement "fref" - an alternative to xref that is able to compute
// links to resources in other families than just "page"

// we need to use this style of processor instead of the simplied function because
// asciidoctor.js can't provide value-less attributes to us
// e.g. in fref:xref[link text], the presence of "link text" attribute needs
// to be checked using Opal and is not present in the simple callaback function
const processor = new Opal.Asciidoctor.Extensions.InlineMacroProcessor()
processor.$initialize()
processor.$process = function (parent, target, attributes) {
    let hash
    let link
    let resolved
    let text
    if (~(hash = target.indexOf("#"))) {
        link = target.substr(0, hash)
        hash = target.substr(hash)
    } else {
        link = target
        hash = ""
    }

    if (attributes.$keys().length == 1) {
        text = Opal.hash_get(attributes, 1)
    }

    if (!(resolved = this.context.contentCatalog.resolveResource(link, this.context, "page"))) {
        return '<a href="' + ("#" + target) + '">' + text + '</a>'
    } else {
        let url = computeRelativeUrlPath(this.context.file.pub.url, resolved.pub.url, hash)
        return '<a href="' + url + '">' + (text || url) + '</a>'
    }
}

module.exports.register = function (registry, context) {
    processor.context = context
    registry.inlineMacro("fref", processor)
}
