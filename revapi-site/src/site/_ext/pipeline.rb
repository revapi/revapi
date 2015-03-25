require 'docs'
require 'coderay'

Awestruct::Extensions::Pipeline.new do
  extension Awestruct::Extensions::Docs::Index.new('/docs', :docs)
  helper Awestruct::Extensions::GoogleAnalytics
end
