function process(parent, target, attributes) {
    if (attributes.$keys().length == 1) {
        let what = Opal.hash_get(attributes, 1)

        target = target.split("@")
        let componentVersion  = target.length == 1 ? null : target[0]
        let componentName = target.length == 2 ? target[1] : target[0]

        let model =  this.context.contentCatalog.getComponent(componentName)
        if (!model) {
            return ""
        }

        let component = null
        if (componentVersion == null) {
            component = model.latest
        } else if (componentVersion == "latest") {
            if (model.versions.length > 1) {
                if (model.versions[0].version == "main") {
                    component = model.versions[1]
                } else {
                    component = model.versions[0]
                }
            } else {
                component = model.versions[0]
            }
        } else {
            component = model.versions.find(c => {
                return c.version == componentVersion
            })
        }

        if (component != null) {
            if (what == "version") {
                return component.version   
            } else if (what == "displayVersion") {
                return component.displayVersion   
            } else if (what == "title") {
                return component.title   
            }
        }
    }
}

module.exports.register = function (registry, context) {
    // we need to use this style of processor instead of the simplied function because
    // asciidoctor.js can't provide value-less attributes to us
    // e.g. in component:some-component[version], the presence of "version" attribute needs
    // to be checked using Opal and is not present in the simple callaback function
    const processor = new Opal.Asciidoctor.Extensions.InlineMacroProcessor()
    processor.$process = process;
    processor.$initialize()
    processor.context = context
    registry.inlineMacro("component", processor)
}
