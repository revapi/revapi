<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#list reports as report>
${report.oldElement} VS ${report.newElement}
</#list>
