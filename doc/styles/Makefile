
XSL=../docbook-xsl/fo.xsl

SOURCES= about.txt creating.txt design.txt files.txt rules.txt rules-filters.txt \
	style-manual.txt $(XSL) 

STYLE_MAIN=style-manual.txt

ATTRS=-a data-uri
COMMON_OPTS=--icons --icons-dir=../common-images/icons

build: html

all: html pdf epub

html: style-manual.html
pdf: style-manual.pdf
epub: style-manual.epub

style-manual.html: $(SOURCES)
	asciidoc $(ATTRS) -a icons -a iconsdir=../common-images/icons -b html5 $(STYLE_MAIN)

style-manual.pdf: $(SOURCES)
	a2x --xsl-file=$(XSL) --fop $(COMMON_OPTS) $(STYLE_MAIN)

style-manual.epub: $(SOURCES)
	a2x --format epub $(COMMON_OPTS) $(STYLE_MAIN)


clean:
	rm -f style-manual.{pdf,epub,html}
