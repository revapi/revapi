'use strict'

let RSS = require("rss")

// copied from Antora
const RESOURCE_ID_RX = /^(?:([^@:$]+)@)?(?:(?:([^@:$]+):)?(?:([^@:$]+))?:)?(?:([^@:$]+)\$)?([^@:$]+)$/
const RESOURCE_ID_RX_GROUP = { version: 1, component: 2, module: 3, family: 4, relative: 5 }

// copied from asciidoctor.js

/**
 * Convert a JSON to an (Opal) Hash.
 */
const toHash = function (object) {
    return object && !object.$$is_hash ? Opal.hash2(Object.keys(object), object) : object
}

/**
 * Convert an (Opal) Hash to JSON.
 */
const fromHash = function (hash) {
    const object = {}
    if (hash) {
        const data = hash.$$smap
        for (const key in data) {
            const value = data[key]
            object[key] = value === Opal.nil ? undefined : value
        }
    }
    return object
}

function matchContentCatalog(xrefGlob, fileSrc, contentCatalog) {
    const match = xrefGlob.match(RESOURCE_ID_RX)
    var relativeMatch = match[RESOURCE_ID_RX_GROUP.relative]
    if (!relativeMatch) return
    relativeMatch = relativeMatch.split('*').filter(s => s.length > 0)

    const version = match[RESOURCE_ID_RX_GROUP.version] || fileSrc.version
    const component = match[RESOURCE_ID_RX_GROUP.component] || fileSrc.component
    const module = match[RESOURCE_ID_RX_GROUP.module] || fileSrc.module
    const family = match[RESOURCE_ID_RX_GROUP.family] || fileSrc.family

    return contentCatalog.getFiles().filter(f => {
        const src = f.src

        if (version !== src.version || component !== src.component || module !== src.module || family !== src.family) {
            return false
        }

        var last = -1
        return relativeMatch.every(item => {
            const idx = src.relative.indexOf(item)
            const ret = idx > last
            if (ret) last = idx
            return ret
        })
    })
}

function summarize(file) {
    var asciidoctor = Opal.Asciidoctor

    var model = asciidoctor.$load(file.src.contents.toString())

    var attrs = fromHash(model.attributes)
    var publishDate = attrs["page-publish_date"]
    var title = attrs["doctitle"]
    var firstPara = model.blocks[0].lines.join("\n")

    if (!publishDate) {
        throw new Error("Publish date not defined in a news article: " + file.src.abspath)
    }

    publishDate = new Date(publishDate)
    if (typeof (publishDate) != "object") {
        throw new Error("Could not parse page-publish_date attribute as a date in " + file.src.abspath)
    }

    var xref = file.src.relative

    return {
        'date': publishDate,
        'title': title,
        'summary': firstPara,
        'xref': xref
    }
}

function generateFeed(context, summaries) {
    let title = context.config.attributes["news-feed-title"]
    let description = context.config.attributes["news-feed-description"]
    let siteUrl = context.config.attributes["site-url"]
    let maxItems = context.config.attributes["news-feed-max-items"]

    let rss = new RSS({
        "title": title,
        "description": description,
        "site_url": siteUrl,
        "pubDate": Date.now(), 
    })

    let items = 0
    for (var i in summaries) {
        if (items++ >= (maxItems || 20)) {
            break
        }

        const summary = summaries[i]

        let page = context.contentCatalog.resolvePage(summary.xref, context.file.src)
        rss.item({
            "title": summary.title,
            "description": summary.summary,
            "url": siteUrl + "/" + page.pub.url,
            "date": summary.date
        })   
    }

    return rss.xml({indent: true})
}

function registerBlock(self, context) {
    self.named("news")
    self.process(function (parent, target, attrs) {
        if (target !== 'generate') {
            return
        }

        const refs = attrs.refs;
        if (!refs) return

        const files = matchContentCatalog(refs, context['file'].src, context.contentCatalog)
        var summaries = files.map(f => summarize(f))
        summaries.sort(function (a, b) {
            return b.date.getTime() - a.date.getTime()
        })

        let maxItems = context.config.attributes["news-feed-max-items"] || 20
        let items = 0
        for (var i in summaries) {
            if (items++ >= maxItems) {
                break
            }
            var summary = summaries[i]
            var date = summary.date.toUTCString()
            date = date.substring(0, date.lastIndexOf(" ", date.length - 5))
            var header = "xref:" + summary.xref + "[" + summary.title + "]"
            
            var section = self.$create_section(parent, header, toHash({}), toHash({}))
            self.parseContent(section, "[.news-date]\n" + date)
            self.parseContent(section, "[.news-summary]\n" + summary.summary)
            parent.append(section)
        }

        context.contentCatalog.addFile({
            contents: Buffer.from(generateFeed(context, summaries)),
            src: {
                component: context.file.src.component,
                version: context.file.src.version,
                module: context.file.src.module,
                family: 'attachment',
                relative: 'news.atom',
                stem: 'news',
                basename: 'news.atom',
                mediaType: 'application/rss+xml'
            }
        })
    })
}

function processInline(parent, target, attributes) {
    if (target !== 'feed') {
        return
    }

    let text
    if (attributes.$keys().length == 1) {
        text = Opal.hash_get(attributes, 1)
    }

    return '<a href="' + this.context.file.pub.moduleRootPath + '/_attachments/news.atom">' + text + '</a>'
}

module.exports.register = function (registry, context) {
    registry.blockMacro(function () {
        registerBlock(this, context)
    })

    let newsInline = new Opal.Asciidoctor.Extensions.InlineMacroProcessor()
    newsInline.$initialize()
    newsInline.context = context
    newsInline.$process = processInline;
    registry.inlineMacro("news", newsInline)
}
