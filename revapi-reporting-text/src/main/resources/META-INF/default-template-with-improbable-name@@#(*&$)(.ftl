<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#list reports as report>
old: ${report.oldElement!"<none>"}
new: ${report.newElement!"<none>"}
<#list report.differences as diff>
${diff.code}<#if diff.description??>: ${diff.description}</#if>
<#list diff.classification?keys as compat>${compat}: ${diff.classification?api.get(compat)}<#sep>, </#list>
</#list>
<#sep>

</#sep>
</#list>
