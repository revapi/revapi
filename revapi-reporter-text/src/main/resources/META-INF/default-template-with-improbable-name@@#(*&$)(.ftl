<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
Analysis results
----------------

Old API: <#list analysis.oldApi.archives as archive>${archive.name}<#sep>, </#list>
New API: <#list analysis.newApi.archives as archive>${archive.name}<#sep>, </#list>
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
<#-- force an empty line at the end -->
<#nt/>
<#t/>
