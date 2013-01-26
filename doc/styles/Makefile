
ASCIIDOC=asciidoc
A2X=a2x

DEST=../../dist/doc

RES=../resources
XSL=$(RES)/docbook-xsl/fo.xsl
ICONSDIR=$(RES)/common-images/icons

SOURCES= about.txt creating.txt design.txt files.txt rules.txt rules-filters.txt \
	style-manual.txt $(XSL) 

STYLE_MAIN=style-manual.txt

ATTRS=-a data-uri
COMMON_OPTS=--icons --icons-dir=$(ICONSDIR)

build: html

all: html pdf epub

html: style-manual.html
pdf: style-manual.pdf
epub: style-manual.epub

style-manual.html: $(SOURCES)
	$(ASCIIDOC) $(ATTRS) -a icons -a iconsdir=$(ICONSDIR) -b html5 $(STYLE_MAIN)

style-manual.pdf: $(SOURCES)
	$(A2X) --xsl-file=$(XSL) --fop $(COMMON_OPTS) $(STYLE_MAIN)

style-manual.epub: $(SOURCES)
	mkdir -p build
	cp -ru $(RES)/common-images build
	$(A2X) --format epub --icons --icons-dir=build/common-images/icons $(STYLE_MAIN)


install: pdf
	cp -u style-manual.pdf $(DEST)

clean:
	rm -f style-manual.{pdf,epub,html}
	rm -rf build
