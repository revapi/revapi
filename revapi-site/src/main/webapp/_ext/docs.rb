module Awestruct
  module Extensions
    module Docs
      class Index
        def initialize(path_prefix='docs', assign_to=:docs)
          @path_prefix = path_prefix
          @assign_to = assign_to
        end

        def execute(site)
          docs = []

          site.pages.each do |page|
            title, href, index = nil

            if (page.relative_source_path =~ /^#{@path_prefix}\//)
              page.href = page.relative_source_path
              docs << page
            end
          end

          docs.sort! { |a, b| (a.index? || 100) <=> (b.index? || 101) }

          site.send("#{@assign_to}=", docs)
        end
      end
    end
  end
end
